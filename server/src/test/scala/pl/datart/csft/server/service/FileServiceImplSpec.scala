package pl.datart.csft.server.service

import better.files._
import cats.effect._
import cats.effect.unsafe.IORuntime
import fs2.text.utf8Encode
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.nio.charset.Charset
import java.nio.file.attribute.PosixFilePermission._
import java.util.UUID

class FileServiceImplSpec extends AsyncWordSpec with Matchers {

  private implicit val ioRuntime: IORuntime = IORuntime.global

  private val outputDirectory: File = File.newTemporaryDirectory().delete(swallowIOExceptions = true)
  private val testContent           = "test content"
  private val testBytes             = testContent.getBytes(Charset.defaultCharset())
  private val testedImplementation  = new FileServiceImpl[IO](outputDirectory)

  "A FileServiceImpl" can {

    "save file" should {

      "successfully save file into a specified location" in {

        testedImplementation
          .saveFile(fs2.Stream(testContent).through(utf8Encode))
          .map(uuid => (outputDirectory / uuid.toString).contentAsString shouldBe testContent)
          .unsafeToFuture()
      }
    }

    "get file" should {

      "successfully get file stream from an existing file" in {

        val uuid     = UUID.randomUUID()
        val testFile = outputDirectory / uuid.toString
        testFile.write(testContent)
        testedImplementation
          .getFile(uuid)
          .flatMap(_.compile.to(Array).map(_ shouldBe testBytes))
          .unsafeToFuture()
      }

      "fail for a not existing file" in {

        val uuid = UUID.randomUUID()
        testedImplementation
          .getFile(uuid)
          .unsafeToFuture()
          .failed
          .map(_ shouldBe a[NoSuchElementException])
      }

      "fail for a directory instead of a file" in {

        val uuid = UUID.randomUUID()
        val _    = (outputDirectory / uuid.toString).createDirectory()
        testedImplementation
          .getFile(uuid)
          .unsafeToFuture()
          .failed
          .map(_.getMessage shouldBe "Requested resource is not a file")
      }

      "fail for an empty file" in {

        val uuid = UUID.randomUUID()
        val _    = (outputDirectory / uuid.toString).createFile()
        testedImplementation
          .getFile(uuid)
          .unsafeToFuture()
          .failed
          .map(_.getMessage shouldBe "Requested resource is empty")
      }

      "fail for an unreadable file" in {

        val uuid     = UUID.randomUUID()
        val testFile = (outputDirectory / uuid.toString).createFile()
        val _        = (
          testFile.write(testContent),
          testFile.setPermissions(
            Set(
              OWNER_WRITE,
              OWNER_EXECUTE,
              GROUP_WRITE,
              GROUP_EXECUTE,
              OTHERS_WRITE,
              OTHERS_EXECUTE
            )
          )
        )

        testedImplementation
          .getFile(uuid)
          .unsafeToFuture()
          .failed
          .map(_.getMessage shouldBe "Requested resource is not readable")
      }
    }
  }
}
