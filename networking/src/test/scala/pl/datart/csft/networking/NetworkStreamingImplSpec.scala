package pl.datart.csft.networking

import cats.effect._
import cats.effect.unsafe.IORuntime
import cats.effect.unsafe.IORuntime.global
import org.http4s.Method._
import org.http4s._
import org.http4s.blaze.client.BlazeClientBuilder
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.io.ByteArrayInputStream

class NetworkStreamingImplSpec extends AsyncWordSpec with Matchers {

  private implicit val ioRuntime: IORuntime = IORuntime.global

  "A NetworkStreamingImpl" can {

    "send streaming request" should {

      "retrieve and pass a response stream" in {
        BlazeClientBuilder[IO](global.compute).resource
          .use { client =>
            val testedImplementation = new NetworkStreamingImpl[IO](client)
            testedImplementation
              .sendStream(
                new ByteArrayInputStream(Array[Byte](0, 0, 0, 0, 0)),
                Uri.unsafeFromString("https://google.com"),
                POST
              )
              .flatMap { responseStream =>
                responseStream.compile.to(Array).map(_ should not be empty)
              }
          }
          .unsafeToFuture()
      }
    }
  }
}
