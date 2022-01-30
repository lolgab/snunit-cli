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
    Main.runBackground(workdir)
    assertEquals(requests.get("http://localhost:9000").text, toSend)
  }
}
