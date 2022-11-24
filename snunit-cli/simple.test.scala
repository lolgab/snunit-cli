//> using lib "org.scalameta::munit:0.7.29"
//> using lib "com.lihaoyi::requests:0.7.0"

import munit._
import scala.util._

class SimpleTest extends FunSuite {
  val port = 9000
  val url = s"http://localhost:$port"
  def runHandler(handler: String, noRuntime: Boolean = false) = {
    val workdir = os.pwd / ".snunit-test" / "test" / "example"
    os.remove.all(workdir)
    os.makeDir.all(workdir)
    os.write(workdir / "handler.scala", handler)
    runBackground(
      Config(workdir, static = None, port, noRuntime)
    )
  }
  test("should run simple example") {
    val toSend = "Simple test"
    runHandler(s"def handler = \"$toSend\"")
    assertEquals(requests.get(url).text(), toSend)
  }
  test("should run with --no-runtime") {
    val toSend = "Simple test"
    val main = s"""
      |//> using scala "3.2.0"
      |import $$dep.`com.github.lolgab::snunit::0.1.1`
      |import snunit._
      |
      |@main
      |def main =
      |  SyncServerBuilder.build(_.send(StatusCode.OK, "$toSend", Seq()))
      |  .listen()
      |""".stripMargin
    runHandler(main, noRuntime = true)
    assertEquals(requests.get(url).text(), toSend)
  }
  test("should support file paths") {
    val toSend = "Simple test"
    val workdir = os.pwd / ".snunit-test" / "test" / "example"
    os.remove.all(workdir)
    os.makeDir.all(workdir)
    val file = workdir / "handler.scala"
    os.write(file, s"def handler = \"$toSend\"")
    runBackground(
      Config(
        file,
        static = None,
        port,
        `no-runtime` = false
      )
    )
    assertEquals(requests.get(url).text(), toSend)
  }
  test("should share static files") {
    val workdir = os.pwd / ".snunit-test" / "test" / "example"
    os.remove.all(workdir)
    val staticDir = workdir / "foo"
    os.makeDir.all(staticDir)
    val file = workdir / "handler.scala"
    os.write(file, s"def handler = \"\"")
    os.write(staticDir / "foo", "bar")
    runBackground(
      Config(
        file,
        static = Some(staticDir),
        port,
        `no-runtime` = false
      )
    )
    assertEquals(requests.get(s"$url/foo").text(), "bar")
  }
  test("Either handler") {
    runHandler(
      """def handler(body: String): Either[String, String] = body match {
        |  case "{}" => Right("OK") 
        |  case _ => Left("Error")
        |}
        |""".stripMargin
    )
    assertEquals(requests.post(url, data = "{}").text(), "OK")
    val leftResponse =
      requests.post(url, data = "something else", check = false)
    assertEquals(leftResponse.text(), "Error")
    assertEquals(leftResponse.statusCode, 500)
  }
  test("Handler with int parameter") {
    runHandler("def handler(i: Int): Int = i + 1")
    assertEquals(requests.post(url, data = "10").text(), "11")
  }

  test("should build a docker image") {
    val workdir = os.pwd / ".snunit-test" / "test" / "example"
    val staticDir = workdir / "static"
    os.remove.all(workdir)
    os.makeDir.all(staticDir)

    val toSend = "Simple test"
    os.write(workdir / "handler.scala", s"def handler = \"$toSend\"")
    os.write(staticDir / "foo", "bar")
    val port = 8080
    val imageName = "simple-test-image"
    buildDocker(
      Config(
        workdir,
        Some(staticDir),
        port,
        `no-runtime` = false
      ),
      imageName
    )
    Using(
      Container(
        os
          .proc("docker", "run", "--rm", "-p", s"$port:$port", "-d", imageName)
          .call()
          .out
          .text()
          .trim
      )
    ) { container =>
      // Wait for Docker to start
      Thread.sleep(100)
      assertEquals(requests.get(s"http://localhost:$port").text(), toSend)
      assertEquals(requests.get(s"http://localhost:$port/foo").text(), "bar")
    }
  }

  override def afterAll(): Unit = {
    os.proc("killall", "unitd").call(check = false)
  }
}
