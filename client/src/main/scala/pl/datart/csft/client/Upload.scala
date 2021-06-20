package pl.datart.csft.client

import cats.effect.unsafe.IORuntime.global
import cats.effect._
import com.typesafe.scalalogging.StrictLogging
import org.http4s.blaze.client.BlazeClientBuilder
import pl.datart.csft.client.config.ClientConfig
import pl.datart.csft.client.config.ClientConfig._
import pl.datart.csft.encryption.Encryption
import pl.datart.csft.networking.NetworkStreamingImpl
import pureconfig.ConfigSource
import pureconfig.generic.auto._

import java.io.File

object Upload extends IOApp with StrictLogging {

  private implicit val asyncIO: Async[IO] = IO.asyncForIO

  override def run(args: List[String]): IO[ExitCode] = {
    args match {
      case inputFile :: passphrase :: _ if passphrase.nonEmpty =>
        BlazeClientBuilder[IO](global.compute).resource.use { client =>
          for {
            clientConfig <- IO.delay(ConfigSource.default.loadOrThrow[ClientConfig]) // loading config for a sever
            encryption   <-
              Encryption.aesEncryptionInstance[IO] // getting an instance of an encrypting tool that uses AES
            networking <-
              IO.delay(new NetworkStreamingImpl[IO](client)) // getting an instance of a network streaming tool
            fileClient <- IO.delay(new CSFTClientImpl[IO](encryption, networking)) // getting an instance of a client
            _          <- IO.delay(logger.info(s"Encrypting and uploading your file..."))
            uuid       <-
              fileClient
                .encryptAndSent(new File(inputFile), clientConfig.uri, passphrase) // encrypting and sending a file
            _ <- IO.delay(logger.info(s"Upload succeeded. Your file fetch ID is: ${uuid.toString}."))
          } yield ExitCode.Success
        }
      case _                                                   =>
        IO
          .delay(logger.error("Invalid arguments. Please, provide path to a valid file and a nonempty passphrase."))
          .map(_ => ExitCode.Error)
    }
  }
}
