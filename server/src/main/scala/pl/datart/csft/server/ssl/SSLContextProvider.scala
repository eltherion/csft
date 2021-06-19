package pl.datart.csft.server.ssl

import better.files.File
import cats.effect._
import cats.implicits._
import org.http4s.blaze.server.BlazeServerBuilder
import pl.datart.csft.server.config.ServerConfig

import java.security._
import javax.net.ssl._

trait SSLContextProvider[F[_]] {
  def getSSLContext(): F[SSLContext]
}

class SSLContextProviderImpl[F[_]](keyStoreLocation: String, keyStorePassphrase: String)(implicit async: Async[F])
    extends SSLContextProvider[F] {
  def getSSLContext(): F[SSLContext] = {
    async.delay {

      val ksStream = File(keyStoreLocation).newInputStream
      assert(Option(ksStream).nonEmpty)

      val ks = KeyStore.getInstance("JKS")
      ks.load(ksStream, keyStorePassphrase.toCharArray)

      val kmf = KeyManagerFactory.getInstance("SunX509")
      kmf.init(ks, keyStorePassphrase.toCharArray)

      val context = SSLContext.getInstance("SSL")

      val tmf =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
      tmf.init(ks)

      context.init(kmf.getKeyManagers, tmf.getTrustManagers, new SecureRandom())

      context
    }
  }

  def secureServer(config: ServerConfig, server: BlazeServerBuilder[F]): F[BlazeServerBuilder[F]] = {
    if (config.sslEnabled) {
      getSSLContext()
        .map(server.withSslContext)
    } else {
      async.pure(server)
    }
  }
}
