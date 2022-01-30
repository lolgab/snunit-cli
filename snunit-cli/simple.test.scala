import $dep.`org.scalameta::munit:0.7.29`
import $dep.`com.lihaoyi::requests:0.7.0`

import munit._

class SimpleTest extends FunSuite {
  test("should run simple example") {
    val workdir = os.pwd / ".snunit-test" / "test" / "example"
    os.remove.all(workdir)
    os.makeDir.all(workdir)

    val toSend = "Simple test"
    os.write(workdir / "handler.scala", s"def handler = \"$toSend\"")
    val port = 9000
    Main.runBackground(workdir, port)
    assertEquals(requests.get(s"http://localhost:$port").text, toSend)
  }

  test("should build a docker image") {
    val workdir = os.pwd / ".snunit-test" / "test" / "example"
    os.remove.all(workdir)
    os.makeDir.all(workdir)

    val toSend = "Simple test"
    os.write(workdir / "handler.scala", s"def handler = \"$toSend\"")
    val port = 8080
    val imageName = "simple-test-image"
    Main.buildDocker(workdir, imageName, port)
    val container = os.proc("docker", "run", "-p", s"$port:$port", "-d", imageName).call().out.text.trim
    assertEquals(requests.get(s"http://localhost:$port").text, toSend)
    os.proc("docker", "kill", container).call()
  }
}
