import cats.data.Validated
import cats.implicits._
import com.monovore.decline._
import scala.util._

given Argument[os.Path] with
  def read(string: String) = try {
      Validated.valid(os.Path(string, os.pwd))
    } catch {
      case _ => Validated.invalidNel(s"invalid path $string. the path should be relative to ${os.pwd}")
    }
  def defaultMetavar = "path"

val pathOpt = Opts.argument[os.Path]()
val staticOpt = Opts.option[os.Path]("static", "path to the directory with static data you want to serve with your app").orNone
val portOpt = Opts.option[Int]("port", "port where to run your application").withDefault(9000)
val noRuntimeFlag = Opts.flag("no-runtime", "disable the function runtime and provide your own SNUnit server").orFalse
val configOpts: Opts[Config] = (pathOpt, staticOpt, portOpt, noRuntimeFlag).mapN(Config.apply)

val runCommand = Opts.subcommand(
  name = "run",
  help = "Run your app on local NGINX Unit"
)(configOpts).map(config => run(config))

val runJvmCommand = Opts.subcommand(
  name = "run-jvm",
  help = "Run your app on the JVM"
)(configOpts).map(config => runJvm(config))

val runBackgroundCommand = Opts.subcommand(
  name = "run-background",
  help = "Run your app in background on a local NGINX Unit"
)(configOpts).map(config => runBackground(config))

val installToolsCommand: Opts[Unit] = Opts.subcommand(
  name = "install-tools",
  help = "Install the tools needed to run apps. Works on Mac OS X (brew) and Ubuntu"
)(Opts.unit).map(_ => installTools())

val dockerImageOpt = Opts.option[String]("docker-image", "Full name of the docker image to build").withDefault("snunit")
val buildDockerCommand = Opts.subcommand(
  name = "build-docker",
  help = "Build a Docker image with your app"
)((configOpts,dockerImageOpt).mapN((config, dockerImage) => buildDocker(config, dockerImage)))

val mainCommand = Command("snunit", "run your Scala Native code as a HTTP server with ease")(
  runCommand orElse
    runBackgroundCommand orElse
    runJvmCommand orElse
    installToolsCommand orElse
    buildDockerCommand
)

object App extends CommandApp(mainCommand)
