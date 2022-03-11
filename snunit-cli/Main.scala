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

  private val scalaNativeVersionArgs = Seq("--native-version", "0.4.4")

  private def prepareSources(path: os.Path, noRuntime: Boolean) = {
    def fail() = sys.exit(1)
    if (!os.exists(path)) {
      println(s"The path $path doesn't exist. Exiting.")
      fail()
    }
    os.makeDir.all(cacheDir)
    val targetDir = cacheDir / path.last.stripSuffix(".scala")
    os.makeDir.all(targetDir)
    if (os.isFile(path)) {
      val dest = targetDir / path.last
      os.remove(dest)
      os.symlink(dest, path)
    } else {
      os.list(path).foreach { p =>
        val dest = targetDir / p.relativeTo(path)
        os.remove(dest)
        os.symlink(dest, p)
      }
    }
    val runtimeDest = targetDir / "runtime.scala"
    val mainDest = targetDir / "snunit-main.scala"
    if (!noRuntime) {
      val runtime = os.read(os.resource / "runtime.scala")
      os.write.over(runtimeDest, runtime)
      os.write.over(mainDest, main)
    } else {
      os.remove(runtimeDest)
      os.remove(mainDest)
    }
    targetDir
  }

  private def buildBinary(
      path: os.Path,
      noRuntime: Boolean,
      scalaCliArgs: Seq[os.Shellable]
  ) = {
    val targetDir = prepareSources(path, noRuntime)
    os.write.over(
      targetDir / "config.scala",
      "//> using platform \"scala-native\""
    )
    val outputPath = cacheDir / s"${path.last}.out"
    os.remove(outputPath)
    os.remove.all(targetDir / ".scala-build" / "project" / "native")
    val proc =
      os.proc(
        "scala-cli",
        "package",
        scalaNativeVersionArgs,
        targetDir,
        "-o",
        outputPath,
        scalaCliArgs
      )
    proc.call(
      stdout = os.Inherit,
      stderr = os.ProcessOutput
        .Readlines(line => if (!line.contains("run it with")) println(line))
    )
    outputPath
  }
  @main
  case class Config(
      @arg(doc = "The path where the handler is") path: os.Path,
      @arg(doc = "Port where the server accepts request") port: Int = 9000,
      @arg(doc = "Run a snunit server without runtime") `no-runtime`: Flag
  )
  implicit val configParser = ParserForClass[Config]

  @main
  def run(config: Config): Unit = {
    val outputPath = buildBinary(config.path, config.`no-runtime`.value, scalaCliArgs = Seq())
    val unitConfig = makeConfig(outputPath, config.port)
    val pid = unitd.run(unitConfig)
    println(s"Unit is running in the background with pid $pid")
  }

  @main
  def runJvm(config: Config): Unit = {
    val targetDir = prepareSources(config.path, config.`no-runtime`.value)
    os.remove(targetDir / "config.scala")
    val outputPath = cacheDir / s"${config.path.last}.out"
    os.remove(outputPath)
    os.remove.all(targetDir / ".scala-build" / "project" / "native")
    val proc = os.proc("scala-cli", "run", scalaNativeVersionArgs, targetDir)
    proc.call(stdout = os.Inherit, env = Map("SNUNIT_PORT" -> config.port.toString))
  }

  @main
  def runBackground(config: Config): Unit = {
    val outputPath = buildBinary(config.path, config.`no-runtime`.value, scalaCliArgs = Seq())
    val unitConfig = makeConfig(outputPath, config.port)
    val pid = unitd.runBackground(unitConfig)
    println(s"Unit is running in the background with pid $pid")
  }

  @main
  def buildDocker(
      config: Config,
      @arg(doc = "Full name of the docker image to build") dockerImage: String =
        "snunit"
  ): Unit = {
    val clangImage = "lolgab/snunit-clang:0.0.2"
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
        config.path,
        config.`no-runtime`.value,
        Seq("--native-clang", clangPath, "--native-clangpp", clangppPath)
      )
      val workdirInContainer = os.root / "workdir"
      val executablePathInContainer = workdirInContainer / outputPath.last
      val unitConfig = makeConfig(executablePathInContainer, config.port)
      val stateDir = cacheDir / "state"
      os.makeDir.all(stateDir)
      val configFile = stateDir / "conf.json"
      os.remove.all(configFile)
      os.write(configFile, unitConfig)
      val stateDirPathInContainer = workdirInContainer / "state"

      val dockerfile = s"""FROM nginx/unit:1.26.1-minimal
        |COPY ${outputPath.last} $executablePathInContainer
        |COPY ${stateDir.last} $stateDirPathInContainer
        |
        |EXPOSE ${config.port}
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
