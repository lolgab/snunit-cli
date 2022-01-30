import $dep.`com.lihaoyi::os-lib:0.8.0`
import $dep.`com.lihaoyi::mainargs:0.2.2`

import mainargs._

object Main {
  private implicit object PathRead
      extends TokensReader[os.Path](
        "path",
        strs => Right(os.Path(strs.head, os.pwd))
      )

  val main = """
    |import snunit._
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
    os.list(os.pwd / "snunit-cli-runtime").filter(_.ext == "scala").foreach { file =>
      os.copy.into(file, targetDir)
    }
    os.write.over(targetDir / "snunit-main.scala", main)
    val outputPath = cacheDir / s"${path.last}.out"
    os.remove(outputPath)
    os.remove.all(targetDir / ".scala-build" / "project" / "native")
    val proc =
      os.proc("scala-cli", "package", targetDir, "-o", outputPath, scalaCliArgs)
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

  @main
  def buildDocker(
      @arg(doc = "The path where the handler is") path: os.Path,
      @arg(doc = "Full name of the docker image to build") dockerImage: String = "snunit",
      @arg(doc = "Port where the server accepts request") port: Int = 9000
  ): Unit = {
    val clangImage = "lolgab/snunit-clang:0.0.2"
    os.proc("docker", "pull", clangImage).call(stdout = os.Inherit)
    val container = os
      .proc(
        "docker",
        "run",
        "-v",
        s"${os.pwd}:${os.pwd}",
        "--rm",
        "-d",
        "-i",
        clangImage
      )
      .call()
      .out
      .text
      .trim
    try {
      def clangScript(entrypoint: String) = s"""#!/bin/bash
        |docker exec $container $entrypoint "$$@" 
        |""".stripMargin

      val clangPath = cacheDir / "clang.sh"
      os.makeDir.all(cacheDir)
      os.remove.all(clangPath)
      os.write(clangPath, clangScript("clang"), perms = "rwxr-xr-x")
      val clangppPath = cacheDir / "clangpp.sh"
      os.remove.all(clangppPath)
      os.write(clangppPath, clangScript("clang++"), perms = "rwxr-xr-x")

      val outputPath = buildBinary(
        path,
        Seq("--native-clang", clangPath, "--native-clangpp", clangppPath)
      )
      val workdirInContainer = os.root / "workdir"
      val executablePathInContainer = workdirInContainer / outputPath.last
      val config = makeConfig(executablePathInContainer, port)
      val stateDir = cacheDir / "state"
      os.makeDir.all(stateDir)
      val configFile = stateDir / "conf.json"
      os.remove.all(configFile)
      os.write(configFile, config)
      val stateDirPathInContainer = workdirInContainer / "state"

      val dockerfile = s"""FROM nginx/unit:1.26.1-minimal
        |COPY ${outputPath.last} $executablePathInContainer
        |COPY ${stateDir.last} $stateDirPathInContainer
        |
        |EXPOSE $port
        |
        |ENTRYPOINT ["unitd", "--no-daemon", "--log", "/dev/stdout", "--state", "$stateDirPathInContainer"]
        |""".stripMargin

      val dockerfilePath = cacheDir / "Dockerfile"
      os.remove.all(dockerfilePath)
      os.write(dockerfilePath, dockerfile)
      os.proc("docker", "build", "-t", dockerImage, ".").call(cwd = cacheDir)
    } finally {
      os.proc("docker", "kill", container).call()
    }
  }

  def main(args: Array[String]): Unit =
    mainargs.ParserForMethods(this).runOrExit(args)
}