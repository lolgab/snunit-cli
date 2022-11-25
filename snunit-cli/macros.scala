import scala.quoted._

def readRuntimeMacro()(using Quotes): Expr[String] =
  import quotes.reflect._
  val runtime = os.read(
    os.Path(
      SourceFile.current.path
    ) / os.up / os.up / "snunit-cli-runtime" / "runtime.scala"
  )
  Expr(runtime)
