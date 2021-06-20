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
    val request   = Request[F]( // creating a request
      method = method,
      uri = uri,
      body = readInputStream(async.delay(inputStream), chunkSize) // putting an input stream to a request body
    )

    async.delay {
      client
        .stream(request) // running the request as a stream
        .flatMap(_.body) // getting response body which is a stream, too
    }
  }
}
