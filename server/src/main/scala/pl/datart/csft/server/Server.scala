package pl.datart.csft.server

import better.files.File
import cats.effect._
import cats.effect.IO._
import org.http4s.blaze.server.BlazeServerBuilder
import pl.datart.csft.server.config.ServerConfig
import pl.datart.csft.server.http.HttpRoutes
import pl.datart.csft.server.service.FileServiceImpl
import pl.datart.csft.server.ssl.SSLContextProviderImpl
import pureconfig._
import pureconfig.generic.auto._

import scala.concurrent.duration.DurationInt

object Server extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    val routes = new HttpRoutes[IO](new FileServiceImpl[IO](outputDirectory = File("./file_data")))

    for {
      config             <- IO.delay(ConfigSource.default.loadOrThrow[ServerConfig])
      sslContextProvider <- IO.delay(new SSLContextProviderImpl[IO](config.keyStoreLocation, config.keyStorePassphrase))
      server             <- IO.delay(
                              BlazeServerBuilder[IO](runtime.compute)
                                .withIdleTimeout(100.seconds)
                                .bindHttp(config.port, config.ip)
                                .withHttpApp(routes.routes)
                            ).flatMap(sslContextProvider.secureServer(config, _))
      exitCode           <- server.resource
                              .use(_ => IO.never)
                              .as(ExitCode.Success)
    } yield exitCode
  }
}
