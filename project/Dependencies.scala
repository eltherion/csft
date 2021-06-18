import sbt._

object Dependencies {
  private val betterFiles     = "com.github.pathikrit"        %% "better-files"                         % VersionsOf.betterFiles
  private val fs2Core         = "co.fs2"                      %% "fs2-core"                             % VersionsOf.fs2
  private val fs2IO           = "co.fs2"                      %% "fs2-io"                               % VersionsOf.fs2
  private val http4sClient    = "org.http4s"                  %% "http4s-blaze-client"                  % VersionsOf.http4s
  private val http4sDsl       = "org.http4s"                  %% "http4s-dsl"                           % VersionsOf.http4s
  private val http4sServer    = "org.http4s"                  %% "http4s-blaze-server"                  % VersionsOf.http4s
  private val janino          = "org.codehaus.janino"         %  "janino"                               % VersionsOf.janino
  private val logbackClassic  = "ch.qos.logback"              %  "logback-classic"                      % VersionsOf.logbackClassic
  private val scalaLogging    = "com.typesafe.scala-logging"  %% "scala-logging"                        % VersionsOf.scalaLogging
  private val scalatest       = "org.scalatest"               %% "scalatest"                            % VersionsOf.scalatest      % Test

  val all: Seq[ModuleID] = Seq(
    betterFiles,
    fs2Core,
    fs2IO,
    janino,
    http4sClient,
    http4sDsl,
    http4sServer,
    logbackClassic,
    scalaLogging,
    scalatest
  )
}