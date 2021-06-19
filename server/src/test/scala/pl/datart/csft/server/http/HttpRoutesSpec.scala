package pl.datart.csft.server.http

import cats.effect._
import cats.effect.unsafe.IORuntime
import fs2.text.utf8Encode
import org.http4s.Method.POST
import org.http4s.{Request, Uri}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import pl.datart.csft.server.service.FileService

import java.nio.charset.Charset
import java.util.UUID

class HttpRoutesSpec extends AsyncWordSpec with Matchers {

  private implicit val ioRuntime: IORuntime = IORuntime.global

  private val testContent  = "test content"
  private val testBytes    = testContent.getBytes(Charset.defaultCharset())
  private val testUUID     = UUID.randomUUID()
  private val errorMessage = "error message"
  private val errorBytes   = errorMessage.getBytes(Charset.defaultCharset())

  private val successfulFileService = new FileService[IO] {
    def saveFile(fileBytes: fs2.Stream[IO, Byte]): IO[UUID] = {
      IO.delay(testUUID)
    }
    def getFile(uuid: UUID): IO[fs2.Stream[IO, Byte]] = {
      IO.delay(fs2.Stream(testContent).through(utf8Encode))
    }
  }

  private val failingFileService = new FileService[IO] {
    def saveFile(fileBytes: fs2.Stream[IO, Byte]): IO[UUID] = IO.raiseError(new Throwable(errorMessage))
    def getFile(uuid: UUID): IO[fs2.Stream[IO, Byte]]       = IO.raiseError(new Throwable(errorMessage))
  }

  "HttpRoutes" can {

    "can answer to a GET request" should {

      "successfully respond" in {
        val testedImplementation = new HttpRoutes[IO](successfulFileService)
        val request              = Request[IO](uri = Uri.unsafeFromString(s"/${UUID.randomUUID().toString}"))
        testedImplementation
          .routes(request)
          .flatMap(_.body.compile.to(Array))
          .map(_ shouldBe testBytes)
          .unsafeToFuture()
      }

      "respond with an error when something happened alongside" in {
        val testedImplementation = new HttpRoutes[IO](failingFileService)
        val request              = Request[IO](uri = Uri.unsafeFromString(s"/${UUID.randomUUID().toString}"))
        testedImplementation
          .routes(request)
          .flatMap(_.body.compile.to(Array))
          .map(_ shouldBe errorBytes)
          .unsafeToFuture()
      }
    }

    "can answer to a proper POST request" should {

      "successfully respond" in {
        val testedImplementation = new HttpRoutes[IO](successfulFileService)
        val request              = Request[IO](
          uri = Uri.unsafeFromString("/"),
          method = POST,
          body = fs2.Stream(testContent).through(utf8Encode)
        )
        testedImplementation
          .routes(request)
          .flatMap(_.body.compile.to(Array))
          .map(new String(_, Charset.defaultCharset()) shouldBe testUUID.toString)
          .unsafeToFuture()
      }

      "respond with an error when something happened alongside" in {
        val testedImplementation = new HttpRoutes[IO](failingFileService)
        val request              = Request[IO](
          uri = Uri.unsafeFromString("/"),
          method = POST,
          body = fs2.Stream(testContent).through(utf8Encode)
        )
        testedImplementation
          .routes(request)
          .flatMap(_.body.compile.to(Array))
          .map(_ shouldBe errorBytes)
          .unsafeToFuture()
      }
    }
  }
}
