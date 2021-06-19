package pl.datart.csft.client

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.http4s._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import pl.datart.csft.encryption.Encryption
import pl.datart.csft.networking.NetworkStreaming
import fs2.text._

import java.io._
import java.util.UUID

class CSFTClientImplSpec extends AsyncWordSpec with Matchers {

  private implicit val ioRuntime: IORuntime = IORuntime.global

  private val uuid       = UUID.randomUUID()
  private val uri        = Uri.unsafeFromString("https://127.0.0.1:8081")
  private val passphrase = "T0p&S3cr3t"

  private val mockedEncryption = new Encryption[IO] {
    def encrypt(inputStream: InputStream, passphrase: String): IO[ByteArrayOutputStream] =
      IO.delay(new ByteArrayOutputStream())
    def encryptFile(fileIn: File, filePathOut: String, passphrase: String): IO[Unit]     = IO.unit
    def decrypt(inputStream: InputStream, passphrase: String): IO[ByteArrayOutputStream] =
      IO.delay(new ByteArrayOutputStream())
    def decryptFile(fileIn: File, filePathOut: String, passphrase: String): IO[Unit]     = IO.unit
  }

  private val mockedNetworking = new NetworkStreaming[IO] {
    def sendStream(inputStream: InputStream, uri: Uri, method: Method): IO[fs2.Stream[IO, Byte]] =
      IO.delay(fs2.Stream(uuid.toString).through(utf8Encode))
  }

  private val testedImplementation = new CSFTClientImpl[IO](mockedEncryption, mockedNetworking)

  "A CSFTClientImpl" can {

    "encrypt and send file" should {

      "perform upload successfully" in {

        val inputFile = better.files.File.newTemporaryFile().toJava
        testedImplementation
          .encryptAndSent(inputFile, uri, passphrase)
          .map(_.toString shouldBe uuid.toString)
          .unsafeToFuture()
      }
    }

    "fetch and decrypt file" should {

      "perform download successfully" in {

        val outputFile = better.files.File.newTemporaryFile()
        testedImplementation
          .fetchAndDecrypt(uuid, uri, passphrase, outputFile.toJava)
          .map(_ shouldBe (()))
          .unsafeToFuture()
      }
    }
  }
}
