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

      val ks = KeyStore.getInstance("JKS") // creating JKS
      ks.load(
        ksStream,
        keyStorePassphrase.toCharArray
      ) // loading the content of the key store file using provided passphrase

      val kmf = KeyManagerFactory.getInstance("SunX509") // creating a factory for key managers
      kmf.init(ks, keyStorePassphrase.toCharArray) // initializing the factory with the key store from above

      val context = SSLContext.getInstance("TLSv1.3") // creating a SSLContext for the TLS v1.3 protocol

      val tmf =
        TrustManagerFactory.getInstance(
          TrustManagerFactory.getDefaultAlgorithm
        )          // creating a factory for trust managers
      tmf.init(ks) // initializing the factory using the key store from the above

      context.init(
        kmf.getKeyManagers,
        tmf.getTrustManagers,
        new SecureRandom()
      ) // finally - initializing SSLContext instance

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
