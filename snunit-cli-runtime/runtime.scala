//> using scala "3.1.1"
//> using platform "scala-native"

import $dep.`com.github.lolgab::snunit::0.0.13`

import snunit._
import snunit.ServerBuilder

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
  case Right(r) => toWritable(req, StatusCode.OK, r)
  case Left(l) => toWritable(req, StatusCode.InternalServerError, l)
  case e: Either[l, r] => e match {
    case Right(r) => toWritable(req, StatusCode.OK, r)
    case Left(l) => toWritable(req, StatusCode.InternalServerError, l)
  }
}

private transparent inline def wrapHandler[T](handler: T): Handler = inline handler match {
  case f: (String => u) =>
    req =>
      val resT = f(req.content)
      toWritable(req, StatusCode.OK, resT)
  case s: T =>
    req => toWritable(req, StatusCode.OK, s)
}