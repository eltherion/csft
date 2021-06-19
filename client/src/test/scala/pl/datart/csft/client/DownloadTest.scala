package pl.datart.csft.client

import better.files.File
import cats.effect.ExitCode
import cats.effect.unsafe.IORuntime
import org.http4s.client.ConnectionFailure
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.util.UUID

class DownloadTest extends AsyncWordSpec with Matchers {

  private implicit val ioRuntime: IORuntime = IORuntime.global

  "Upload" should {
    "fail on empty argument list" in {
      Download
        .run(List())
        .map(_ shouldBe ExitCode.Error)
        .unsafeToFuture()
    }

    "fail on upload to a nonexistent server" in {
      Download
        .run(List(UUID.randomUUID().toString, "T0p&S3cr3t", File.newTemporaryFile().pathAsString))
        .unsafeToFuture()
        .failed
        .map(_.getClass shouldBe classOf[ConnectionFailure])
    }
  }
}
