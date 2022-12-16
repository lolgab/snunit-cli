import scala.util._

inline def runtime: String = ${ readRuntimeMacro() }

val main = """import snunit._
  |
  |@main
  |def main =
  |  SyncServerBuilder.build(wrapHandler(handler))
  |  .listen()
  |""".stripMargin

def makeConfig(
    executable: os.Path,
    publicDirOpt: Option[os.Path],
    port: Int
) = {
  def passToApp = ujson.Obj("pass" -> "applications/app")
  ujson.Obj(
    "listeners" -> ujson.Obj(
      s"*:$port" -> ujson.Obj("pass" -> "routes")
    ),
    "routes" -> ujson.Arr(
      ujson.Obj("action" -> (publicDirOpt match {
        case Some(publicDir) =>
          ujson.Obj("share" -> s"$publicDir$$uri", "fallback" -> passToApp)
        case None =>
          passToApp
      }))
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

private val snunitConfigFile = os.rel / "snunit-cli-config.scala"

private val scalaNativeVersionArgs = Seq("--native-version", "0.4.9")

private def cleanCache() = os.remove.all(cacheDir)

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
  val runtimeDest = targetDir / "snunit-runtime.scala"
  val mainDest = targetDir / "snunit-main.scala"
  if (!noRuntime) {
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
  os.write(
    targetDir / snunitConfigFile,
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

case class Config(
    path: os.Path,
    static: Option[os.Path],
    port: Int,
    `no-runtime`: Boolean,
    scalaCliExtraArgs: os.Shellable
)

def run(config: Config): Unit = {
  cleanCache()
  val outputPath = buildBinary(
    config.path,
    config.`no-runtime`,
    scalaCliArgs = Seq(config.scalaCliExtraArgs)
  )
  val unitConfig = makeConfig(outputPath, config.static, config.port)
  unitd.run(unitConfig)
}

def runJvm(config: Config): Unit = {
  cleanCache()
  val targetDir = prepareSources(config.path, config.`no-runtime`)
  os.remove(targetDir / snunitConfigFile)
  val outputPath = cacheDir / s"${config.path.last}.out"
  os.remove(outputPath)
  os.remove.all(targetDir / ".scala-build" / "project" / "native")
  val proc = os.proc("scala-cli", "run", scalaNativeVersionArgs, targetDir)
  proc.call(
    stdout = os.Inherit,
    env = Map("SNUNIT_PORT" -> config.port.toString)
  )
}

def runBackground(config: Config): Unit = {
  cleanCache()
  val outputPath = buildBinary(
    config.path,
    config.`no-runtime`,
    scalaCliArgs = Seq(config.scalaCliExtraArgs)
  )
  val unitConfig = makeConfig(outputPath, config.static, config.port)
  val pid = unitd.runBackground(unitConfig)
  println(s"Unit is running in the background with pid $pid")
}

def installTools(): Unit = {
  val isBrewInstalled =
    os.proc("bash", "-c", "command -v brew").call(check = false).exitCode == 0
  if (isBrewInstalled) installWithBrew()
  else installWithAptGet()
}

def buildDocker(
    config: Config,
    dockerImage: String
): Unit = {
  cleanCache()
  val clangImage = "lolgab/snunit-clang:0.0.3"
  Using(
    Container(
      os.proc(
        "docker",
        "run",
        "-v",
        s"${os.pwd}:${os.pwd}",
        "--rm",
        "-d",
        "-i",
        clangImage
      ).call()
        .out
        .text()
        .trim
    )
  ) { container =>
    def clangScript(entrypoint: String) = s"""#!/bin/bash
      |docker exec $container $entrypoint "$$@" 
      |""".stripMargin

    val clangPath = cacheDir / "clang.sh"
    os.remove.all(cacheDir)
    os.makeDir.all(cacheDir)
    os.write(clangPath, clangScript("clang"), perms = "rwxr-xr-x")
    val clangppPath = cacheDir / "clangpp.sh"
    os.write(clangppPath, clangScript("clang++"), perms = "rwxr-xr-x")

    val outputPath = buildBinary(
      config.path,
      config.`no-runtime`,
      Seq(
        "--native-clang",
        clangPath,
        "--native-clangpp",
        clangppPath,
        config.scalaCliExtraArgs
      )
    )
    val workdirInContainer = os.root / "workdir"
    val staticDirInContainer = workdirInContainer / "static"
    val executablePathInContainer = workdirInContainer / outputPath.last
    val unitConfig = makeConfig(
      executablePathInContainer,
      config.static.map(_ => staticDirInContainer),
      config.port
    )
    val stateDir = cacheDir / "state"
    os.makeDir.all(stateDir)
    val staticInCacheDir = config.static.map { static =>
      val targetStatic = cacheDir / "static"
      os.makeDir.all(targetStatic)
      os.list(static).foreach(os.copy.into(_, targetStatic))
      targetStatic
    }
    val configFile = stateDir / "conf.json"
    os.remove.all(configFile)
    os.write(configFile, unitConfig)
    val stateDirPathInContainer = workdirInContainer / "state"

    val dockerfile = s"""FROM nginx/unit:1.29.0-minimal
      |COPY ${outputPath.last} $executablePathInContainer
      |COPY ${stateDir.last} $stateDirPathInContainer
      |${config.static.fold("")(static => s"COPY static $staticDirInContainer")}
      |
      |EXPOSE ${config.port}
      |
      |ENTRYPOINT ["unitd", "--no-daemon", "--log", "/dev/stdout", "--state", "$stateDirPathInContainer"]
      |""".stripMargin

    val dockerfilePath = cacheDir / "Dockerfile"
    os.remove.all(dockerfilePath)
    os.write(dockerfilePath, dockerfile)
    os.proc("docker", "build", "-t", dockerImage, ".").call(cwd = cacheDir)
    println(
      s"""
          |Your docker image was built. Run it with:
          |docker run --rm -p ${config.port}:${config.port} $dockerImage""".stripMargin
    )
  }
}
