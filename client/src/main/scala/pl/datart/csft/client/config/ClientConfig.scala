package pl.datart.csft.client.config

import org.http4s.Uri
import pureconfig.ConfigReader

final case class ClientConfig(uri: Uri)

object ClientConfig {
  implicit val uriReader: ConfigReader[Uri] = ConfigReader[String].map(Uri.unsafeFromString)
}
