package pl.datart.csft.client

import cats.effect.ExitCode
import cats.effect.unsafe.IORuntime
import org.http4s.client.ConnectionFailure
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class UploadTest extends AsyncWordSpec with Matchers {

  private implicit val ioRuntime: IORuntime = IORuntime.global

  "Upload" should {
    "fail on empty argument list" in {
      Upload
        .run(List())
        .map(_ shouldBe ExitCode.Error)
        .unsafeToFuture()
    }

    "fail on upload to a nonexistent server" in {
      Upload
        .run(List(getClass.getResource("/bouncy_castle.gif").getFile, "T0p&S3cr3t"))
        .unsafeToFuture()
        .failed
        .map(_.getClass shouldBe classOf[ConnectionFailure])
    }
  }
}
