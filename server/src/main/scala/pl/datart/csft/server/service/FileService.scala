package pl.datart.csft.server.service

import better.files.File
import cats.effect._
import cats.syntax.flatMap._
import cats.syntax.functor._
import fs2.io._
import fs2.io.file._

import java.util.UUID

trait FileService[F[_]] {
  def saveFile(fileBytes: fs2.Stream[F, Byte]): F[UUID]
  def getFile(uuid: UUID): F[fs2.Stream[F, Byte]]
}

class FileServiceImpl[F[_]](outputDirectory: File)(implicit async: Async[F]) extends FileService[F] {
  private val chunkSize = 1024

  def saveFile(fileBytes: fs2.Stream[F, Byte]): F[UUID] = {
    for {
      uuid       <- async.delay(UUID.randomUUID())                                      // getting a random UUID
      _          <- async.delay {
                      if (!outputDirectory.exists) { // creating an output directory if it's not existing
                        outputDirectory.createDirectory()
                      }
                    }
      outputFile <- async.delay(outputDirectory / uuid.toString)                        // creating an output file
      _          <- fileBytes.through(Files[F].writeAll(outputFile.path)).compile.drain // saving bytes stream to the output file
    } yield uuid
  }

  def getFile(uuid: UUID): F[fs2.Stream[F, Byte]] = {
    val searchPath = (outputDirectory / uuid.toString)
    // testing various conditions to be fulfilled
    if (!searchPath.exists) {
      async.raiseError(new NoSuchElementException)
    } else if (searchPath.isDirectory) {
      async.raiseError(new IllegalStateException("Requested resource is not a file"))
    } else if (searchPath.isEmpty) {
      async.raiseError(new IllegalStateException("Requested resource is empty"))
    } else if (!searchPath.isReadable) {
      async.raiseError(new IllegalStateException("Requested resource is not readable"))
    } else {
      async.delay(
        readInputStream(async.delay(searchPath.newInputStream), chunkSize)
      ) // returning file content as a bytes stream
    }
  }
}
