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
      encryptedFile <- async.delay(File.newTemporaryFile()) // a new temporary file to store encrypted content
      _             <- encryption.encryptFile(
                         inputFile,
                         encryptedFile.pathAsString,
                         passphrase
                       ) // encrypting using given output path and passphrase
      inputStream   <- async.delay(encryptedFile.newInputStream) // getting an InputStream from the encrypted file
      responseBytes <- networking.sendStream(
                         inputStream,
                         uri,
                         POST
                       ) // and sending it through the network using given service uri and HTTP method
      uuidString <- responseBytes
                      .take(uuidStringLength)
                      .through(utf8Decode)
                      .compile
                      .lastOrError // getting server response as a string
      uuid <- async.delay(UUID.fromString(uuidString)) // getting a file UUID from the response
      _    <- async.delay(inputStream.close())         // tidying up
      _    <- async.delay(encryptedFile.delete())      // tidying up
    } yield uuid
  }
  def fetchAndDecrypt(uuid: UUID, uri: Uri, passphrase: String, outputFile: JFile): F[Unit] = {
    for {
      uuidStream <- async.delay(
                      new ByteArrayInputStream(uuid.toString.getBytes(Charset.defaultCharset()))
                    ) // creating bytes stream from the file UUID
      responseBytes <-
        networking.sendStream(uuidStream, uri.addPath(uuid.toString), GET) // asking for an encrypted file
      encryptedFile <- async.delay(File.newTemporaryFile()) // a new temporary file to store encrypted content
      _             <- responseBytes
                         .through(Files[F].writeAll(encryptedFile.path))
                         .compile
                         .drain // writing bytes from the response to the temp file
      _ <- encryption.decryptFile(
             encryptedFile.toJava,
             outputFile.getAbsolutePath,
             passphrase
           ) // decrypting file to a given output path
      _ <- async.delay(encryptedFile.delete()) // tidying up
    } yield ()
  }
}
