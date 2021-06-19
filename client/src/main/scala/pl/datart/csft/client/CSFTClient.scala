package pl.datart.csft.client

import better.files._
import cats.effect._
import cats.implicits._
import fs2.io.file.Files
import fs2.text.utf8Decode
import org.http4s.Method._
import org.http4s.Uri
import pl.datart.csft.encryption.Encryption
import pl.datart.csft.networking.NetworkStreaming

import java.io.{ByteArrayInputStream, File => JFile}
import java.nio.charset.Charset
import java.util.UUID

trait CSFTClient[F[_]] {
  def encryptAndSent(inputFile: JFile, uri: Uri, passphrase: String): F[UUID]
  def fetchAndDecrypt(uuid: UUID, uri: Uri, passphrase: String, outputFile: JFile): F[Unit]
}

class CSFTClientImpl[F[_]](encryption: Encryption[F], networking: NetworkStreaming[F])(implicit async: Async[F]) {

  private val uuidStringLength = 36L

  def encryptAndSent(inputFile: JFile, uri: Uri, passphrase: String): F[UUID] = {
    for {
      encryptedFile <- async.delay(File.newTemporaryFile())
      _             <- encryption.encryptFile(inputFile, encryptedFile.pathAsString, passphrase)
      inputStream   <- async.delay(encryptedFile.newInputStream)
      responseBytes <- networking.sendStream(inputStream, uri, POST)
      uuidString    <- responseBytes.take(uuidStringLength).through(utf8Decode).compile.lastOrError
      uuid          <- async.delay(UUID.fromString(uuidString))
      _             <- async.delay(inputStream.close())
      _             <- async.delay(encryptedFile.delete())
    } yield uuid
  }
  def fetchAndDecrypt(uuid: UUID, uri: Uri, passphrase: String, outputFile: JFile): F[Unit] = {
    for {
      uuidStream    <- async.delay(new ByteArrayInputStream(uuid.toString.getBytes(Charset.defaultCharset())))
      responseBytes <- networking.sendStream(uuidStream, uri.addPath(uuid.toString), GET)
      encryptedFile <- async.delay(File.newTemporaryFile())
      _             <- responseBytes.through(Files[F].writeAll(encryptedFile.path)).compile.drain
      _             <- encryption.decryptFile(encryptedFile.toJava, outputFile.getAbsolutePath, passphrase)
      _             <- async.delay(encryptedFile.delete())
    } yield ()
  }
}
