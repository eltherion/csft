package pl.datart.csft.server.service

import better.files.File
import cats.syntax.functor._
import cats.syntax.flatMap._
import cats.effect._

import java.util.UUID

trait FileService[F[_]] {
  def saveFile(fileBytes: fs2.Stream[F, Byte]): F[UUID]
  def getFile(uuid: UUID): F[fs2.Stream[F, Byte]]
}

class FileServiceImpl[F[_]](outputDirectory: File)(implicit async: Async[F]) extends FileService[F] {
  private val chunkSize = 1024

  def saveFile(fileBytes: fs2.Stream[F, Byte]): F[UUID] = {
    for {
      uuid       <- async.delay(UUID.randomUUID())
      _          <- async.delay {
                      if (!outputDirectory.exists) {
                        outputDirectory.createDirectory()
                      }
                    }
      outputFile <- async.delay(outputDirectory / uuid.toString)
      _          <- fileBytes
                      .chunkLimit(chunkSize)
                      .map(chunks => outputFile.appendBytes(chunks.iterator))
                      .compile
                      .drain
                      .map(_ => uuid)
    } yield uuid
  }

  def getFile(uuid: UUID): F[fs2.Stream[F, Byte]] = {
    val searchPath = (outputDirectory / uuid.toString)
    if (!searchPath.exists) {
      async.raiseError(new NoSuchElementException)
    } else if (searchPath.isDirectory) {
      async.raiseError(new IllegalStateException("Requested resource is not a file"))
    } else if (searchPath.isEmpty) {
      async.raiseError(new IllegalStateException("Requested resource is empty"))
    } else if (!searchPath.isReadable) {
      async.raiseError(new IllegalStateException("Requested resource is not readable"))
    } else {
      async.delay(fs2.Stream.fromIterator(searchPath.bytes, chunkSize))
    }
  }
}
