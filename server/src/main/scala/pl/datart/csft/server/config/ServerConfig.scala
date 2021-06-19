package pl.datart.csft.server.config

final case class ServerConfig(
    ip: String,
    port: Int,
    sslEnabled: Boolean,
    keyStoreLocation: String,
    keyStorePassphrase: String
)
