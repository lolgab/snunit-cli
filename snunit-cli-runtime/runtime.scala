//> using scala "3.2.0"

//> using lib "com.github.lolgab::snunit::0.1.1"

import snunit._

import scala.compiletime.erasedValue
import scala.util.control.NonFatal

private inline def handlerImpl(req: Request, code: StatusCode, res: String, contentType: String): Unit =
  try {
    req.send(code, res, Seq("Content-type" -> "text/plain"))
  } catch {
    case NonFatal(e) =>
      req.send(StatusCode.InternalServerError, s"Got error $e", Seq.empty)
  }

private inline def textPlainResponse(req: Request, code: StatusCode, res: String): Unit = handlerImpl(req, code, res, "text/plain")

private transparent inline def toWritable[T](req: Request, code: StatusCode, t: T): Unit = inline t match {
  case s: String => textPlainResponse(req, code, s)
  case i: Int => textPlainResponse(req, code, i.toString)
  case Right(r) => toWritable(req, StatusCode.OK, r)
  case Left(l) => toWritable(req, StatusCode.InternalServerError, l)
  case e: Either[l, r] => e match {
    case Right(r) => toWritable(req, StatusCode.OK, r)
    case Left(l) => toWritable(req, StatusCode.InternalServerError, l)
  }
}

private transparent inline def readContent[T](content: String) = {
  val result = inline erasedValue[T] match {
    case _: String => content
    case _: Int => content.toInt
  }
  result.asInstanceOf[T]
}

private transparent inline def wrapHandler[T](handler: T): Handler = inline handler match {
  case f: (t => u) =>
    req =>
      val contentT = readContent[t](req.content)
      val resT = f(contentT)
      toWritable(req, StatusCode.OK, resT)
  case s: T =>
    req => toWritable(req, StatusCode.OK, s)
}
