package pl.datart.csft.encryption

import better.files.File
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class EncryptionImplSpec extends AsyncWordSpec with Matchers {

  private implicit val ioRuntime: IORuntime = IORuntime.global

  private val passphrase           = "T0p&S3cr3t"
  private val inputFile            = File(getClass.getResource("/bouncy_castle.gif").getFile)
  private val encryptedFile        = File(getClass.getResource("/bouncy_castle_encrypted.gif").getFile)
  private val testedImplementation = Encryption.aesEncryptionInstance[IO]

  "An EncryptionImpl" can {

    "encrypt file" should {

      "encrypt file successfully with a provided passphrase" in {
        val outputfile = File.newTemporaryFile()

        testedImplementation
          .flatMap(_.encryptFile(inputFile.toJava, outputfile.pathAsString, passphrase))
          .map { _ =>
            outputfile.byteArray should not be inputFile.byteArray
          }
          .unsafeToFuture()
      }
    }

    "decrypt file" should {

      "decrypt file successfully with a provided passphrase" in {
        val outputfile = File.newTemporaryFile()

        testedImplementation
          .flatMap(_.decryptFile(encryptedFile.toJava, outputfile.pathAsString, passphrase))
          .map { _ =>
            outputfile.byteArray shouldBe inputFile.byteArray
          }
          .unsafeToFuture()
      }
    }
  }
}
