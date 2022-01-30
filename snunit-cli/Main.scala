import $dep.`com.lihaoyi::os-lib:0.8.0`
import $dep.`com.lihaoyi::mainargs:0.2.2`

import mainargs._

object Main {
  private implicit object PathRead
      extends TokensReader[os.Path](
        "path",
        strs => Right(os.Path(strs.head, os.pwd))
      )

  val main = """//> using scala "3.1.1"
    |//> using platform "scala-native"
    |
    |import $dep.`com.github.lolgab::snunit::0.0.13`
    |import $dep.`com.lihaoyi::upickle::1.5.0`
    |
    |import snunit._
    |import snunit.ServerBuilder
    |
    |import scala.compiletime.error
    |import scala.util.control.NonFatal
    |
    |private inline def handlerImpl(req: Request, res: String, contentType: String): Unit =
    |  try {
    |    req.send(StatusCode.OK, res, Seq("Content-type" -> "text/plain"))
    |  } catch {
    |    case NonFatal(e) =>
    |      req.send(StatusCode.InternalServerError, s"Got error $e", Seq.empty)
    |  }
    |
    |private transparent inline def wrapHandler(handler: Any): Handler = inline handler match {
    |  case s: String => req => handlerImpl(req, s, "text/plain")
    |  case f: (String => String) => req => handlerImpl(req, f(req.content), "text/plain")
    |  case f: (String => Either[Throwable, String]) => req => f(req.content) match {
    |    case Right(value) => req.send(StatusCode.OK, value, Seq("Content-type" -> "text/plain"))
    |    case Left(e) => req.send(StatusCode.InternalServerError, s"Got error $e", Seq("Content-type" -> "text/plain"))
    |  }
    |  case other => error("handler type not supported")
    |}
    |
    |@main
    |def main =
    |  SyncServerBuilder.build(wrapHandler(handler))
    |  .listen()
    |""".stripMargin

  def makeConfig(executable: os.Path, port: Int) = {
    ujson.Obj(
      "listeners" -> ujson.Obj(
        s"*:$port" -> ujson.Obj("pass" -> "applications/app")
      ),
      "applications" -> ujson.Obj(
        "app" -> ujson.Obj(
          "type" -> "external",
          "executable" -> executable.toString
        )
      )
    )
  }

  private val cacheDir = os.pwd / ".snunit"

  private def buildBinary(path: os.Path, scalaCliArgs: Seq[os.Shellable]) = {
    def fail() = sys.exit(1)
    if (!os.exists(path)) {
      println(s"The path $path doesn't exist. Exiting.")
      fail()
    }
    val targetDir = cacheDir / path.last
    os.makeDir.all(cacheDir)
    os.copy.over(path, targetDir)
    os.write.over(targetDir / "snunit-main.scala", main)
    val outputPath = cacheDir / s"${path.last}.out"
    os.remove(outputPath)
    os.remove.all(targetDir / ".scala-build" / "project" / "native")
    val proc = os.proc("scala-cli", "package", targetDir, "-o", outputPath, scalaCliArgs)
    proc.call(
      stdout = os.Inherit,
      stderr = os.ProcessOutput
        .Readlines(line => if (!line.contains("run it with")) println(line))
    )
    outputPath
  }

  @main
  def run(
      @arg(doc = "The path where the handler is") path: os.Path,
      @arg(doc = "Port where the server accepts request") port: Int = 9000
  ): Unit = {
    val outputPath = buildBinary(path, scalaCliArgs = Seq())
    val config = makeConfig(outputPath, port)
    val pid = unitd.run(config)
    println(s"Unit is running in the background with pid $pid")
  }

  @main
  def runBackground(
      @arg(doc = "The path where the handler is") path: os.Path,
      @arg(doc = "Port where the server accepts request") port: Int = 9000
  ): Unit = {
    val outputPath = buildBinary(path, scalaCliArgs = Seq())
    val config = makeConfig(outputPath, port)
    val pid = unitd.runBackground(config)
    println(s"Unit is running in the background with pid $pid")
  }

  def main(args: Array[String]): Unit =
    mainargs.ParserForMethods(this).runOrExit(args)
}
