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

  val routes: Kleisli[F, Request[F], Response[F]] = HttpRoutes
    .of[F] {
      case GET -> Root / UUIDVar(uuid) =>
        fileService
          .getFile(uuid)
          .map { fileStream =>
            Response(body = fileStream)
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
          .saveFile(req.body)
          .map { uuid =>
            Response[F](body = fs2.Stream(uuid.toString).through(utf8Encode))
          }
          .recover {
            case error =>
              Response(
                status = Status.InternalServerError,
                body = Stream(error.getMessage).through(utf8Encode)
              )
          }
    }
    .orNotFound

}
