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
import java.util.UUID

object Download extends IOApp with StrictLogging {

  private implicit val asyncIO: Async[IO] = IO.asyncForIO

  override def run(args: List[String]): IO[ExitCode] = {
    args match {
      case uuid :: passphrase :: outputFile :: _ if passphrase.nonEmpty =>
        BlazeClientBuilder[IO](global.compute).resource.use { client =>
          for {
            uuid         <- IO.delay(UUID.fromString(uuid))
            clientConfig <- IO.delay(ConfigSource.default.loadOrThrow[ClientConfig])
            encryption   <- Encryption.aesEncryptionInstance[IO]
            networking   <- IO.delay(new NetworkStreamingImpl[IO](client))
            fileClient   <- IO.delay(new CSFTClientImpl[IO](encryption, networking))
            _            <- IO.delay(logger.info(s"Download and decrypting your file..."))
            _            <- fileClient.fetchAndDecrypt(uuid, clientConfig.uri, passphrase, new File(outputFile))
            _            <- IO.delay(logger.info(s"Download succeeded. Your file location is: $outputFile."))
          } yield ExitCode.Success
        }
      case _                                                            =>
        IO
          .delay(
            logger.error(
              "Invalid arguments. Please, provide a valid file UUID, a nonempty passphrase and a desired output file in an existing directory."
            )
          )
          .map(_ => ExitCode.Error)
    }

  }
}
