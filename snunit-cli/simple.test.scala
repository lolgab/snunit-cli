import $dep.`org.scalameta::munit:0.7.29`
import $dep.`com.lihaoyi::requests:0.7.0`

import munit._

class SimpleTest extends FunSuite {
  val port = 9000
  val url = s"http://localhost:$port"
  def runHandler(handler: String, noRuntime: Boolean = false) = {
    val workdir = os.pwd / ".snunit-test" / "test" / "example"
    os.remove.all(workdir)
    os.makeDir.all(workdir)
    os.write(workdir / "handler.scala", handler)
    Main.runBackground(Main.Config(workdir, port, mainargs.Flag(noRuntime)))
  }
  test("should run simple example") {
    val toSend = "Simple test"
    runHandler(s"def handler = \"$toSend\"")
    assertEquals(requests.get(url).text, toSend)
  }
  test("should run with --no-runtime") {
    val toSend = "Simple test"
    val main = s"""
      |//> using scala "3.1.1"
      |import $$dep.`com.github.lolgab::snunit::0.0.18`
      |import snunit._
      |
      |@main
      |def main =
      |  SyncServerBuilder.build(_.send(StatusCode.OK, "$toSend", Seq()))
      |  .listen()
      |""".stripMargin
    runHandler(main, noRuntime = true)
    assertEquals(requests.get(url).text, toSend)
  }
  test("should support file paths") {
    val toSend = "Simple test"
    val workdir = os.pwd / ".snunit-test" / "test" / "example"
    os.remove.all(workdir)
    os.makeDir.all(workdir)
    val file = workdir / "handler.scala"
    os.write(file, s"def handler = \"$toSend\"")
    Main.runBackground(Main.Config(file, port, `no-runtime` = mainargs.Flag(false)))
    assertEquals(requests.get(url).text, toSend)
  }
  test("Either handler") {
    runHandler(
      """def handler(body: String): Either[String, String] = body match {
        |  case "{}" => Right("OK") 
        |  case _ => Left("Error")
        |}
        |""".stripMargin
    )
    assertEquals(requests.post(url, data = "{}").text, "OK")
    val leftResponse =
      requests.post(url, data = "something else", check = false)
    assertEquals(leftResponse.text, "Error")
    assertEquals(leftResponse.statusCode, 500)
  }
  test("Handler with int parameter") {
    runHandler("def handler(i: Int): Int = i + 1")
    assertEquals(requests.post(url, data = "10").text, "11")
  }

  test("should build a docker image") {
    val workdir = os.pwd / ".snunit-test" / "test" / "example"
    os.remove.all(workdir)
    os.makeDir.all(workdir)

    val toSend = "Simple test"
    os.write(workdir / "handler.scala", s"def handler = \"$toSend\"")
    val port = 8080
    val imageName = "simple-test-image"
    Main.buildDocker(
      Main.Config(workdir, port, `no-runtime` = mainargs.Flag(false)),
      imageName
    )
    val container = os
      .proc("docker", "run", "-p", s"$port:$port", "-d", imageName)
      .call()
      .out
      .text
      .trim
    // Wait for Docker to start
    Thread.sleep(100)
    try {
      assertEquals(requests.get(s"http://localhost:$port").text, toSend)
    } finally {
      os.proc("docker", "kill", container).call(check = false)
    }
  }

  override def afterAll(): Unit = {
    os.proc("killall", "unitd").call(check = false)
  }
}
