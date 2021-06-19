package pl.datart.csft.server.ssl

import cats.effect._
import cats.effect.unsafe.IORuntime
import org.http4s.blaze.server.BlazeServerBuilder
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import pl.datart.csft.server.config.ServerConfig

import javax.net.ssl.SSLContext

class SSLContextProviderImplSpec extends AsyncWordSpec with Matchers {

  private implicit val ioRuntime: IORuntime = IORuntime.global

  private val jksLocation          = getClass.getResource("/server.jks").getFile
  private val jksPassword          = "T0p&S3cr3t"
  private val testedImplementation = new SSLContextProviderImpl[IO](jksLocation, jksPassword)

  "A SSLContextProviderImpl" can {

    "provide a SSL context" should {

      "get it for valid parameters" in {

        testedImplementation
          .getSSLContext()
          .map(_.getClass shouldBe classOf[SSLContext])
          .unsafeToFuture()
      }
    }

    "secure provided server with SSL" should {

      "not use SSL context if not specified" in {

        val port   = 8081
        val config = ServerConfig("0.0.0.0", port, sslEnabled = false, jksLocation, jksPassword)
        val server = BlazeServerBuilder[IO](executionContext)

        testedImplementation
          .secureServer(config, server)
          .map(_ shouldBe server) //unchanged
          .unsafeToFuture()
      }

      "use SSL context if specified" in {

        val port   = 8081
        val config = ServerConfig("0.0.0.0", port, sslEnabled = true, jksLocation, jksPassword)
        val server = BlazeServerBuilder[IO](executionContext)

        testedImplementation
          .secureServer(config, server)
          .map(_ should not be server) //changed
          .unsafeToFuture()
      }
    }
  }
}
