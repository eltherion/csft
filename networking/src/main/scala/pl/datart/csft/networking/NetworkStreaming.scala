package pl.datart.csft.networking

import cats.effect._
import fs2.io._
import org.http4s._
import org.http4s.client.Client

import java.io._

trait NetworkStreaming[F[_]] {
  def sendStream(inputStream: InputStream, uri: Uri, method: Method): F[fs2.Stream[F, Byte]]
}

class NetworkStreamingImpl[F[_]](client: Client[F])(implicit async: Async[F]) extends NetworkStreaming[F] {

  def sendStream(inputStream: InputStream, uri: Uri, method: Method): F[fs2.Stream[F, Byte]] = {
    val chunkSize = 1024
    val request   = Request[F](
      method = method,
      uri = uri,
      body = readInputStream(async.delay(inputStream), chunkSize)
    )

    async.delay {
      client
        .stream(request)
        .flatMap(_.body)
    }
  }
}
