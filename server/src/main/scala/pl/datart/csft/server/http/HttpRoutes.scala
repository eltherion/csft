package pl.datart.csft.server.http

import cats.data.Kleisli
import cats.syntax.all._
import cats.effect._
import fs2._
import fs2.text.utf8Encode
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
import pl.datart.csft.server.service.FileService

class HttpRoutes[F[_]](fileService: FileService[F])(implicit async: Async[F]) {

  // routes with two endpoints:
  // POST - awaiting for the incoming files
  // GET - serving a file for a given UUID

  val routes: Kleisli[F, Request[F], Response[F]] = HttpRoutes
    .of[F] {
      case GET -> Root / UUIDVar(uuid) => // extracting UUID from a request
        fileService
          .getFile(uuid) // finding a file for a given UUID
          .map { fileStream =>
            Response(body = fileStream) // packing as a stream into a body of the response
          }
          .recover {
            case error =>
              Response(
                status = Status.InternalServerError,
                body = Stream(error.getMessage).through(utf8Encode)
              )
          }
      case req @ POST -> Root          =>
        fileService
          .saveFile(req.body) // extracting a file content from a request body
          .map { uuid =>
            Response[F](body =
              fs2.Stream(uuid.toString).through(utf8Encode)
            ) // creating a response with an assigned UUID in a body
          }
          .recover {
            case error =>
              Response(
                status = Status.InternalServerError,
                body = Stream(error.getMessage).through(utf8Encode)
              )
          }
    }
    .orNotFound // ignoring all the other not matching requests

}
