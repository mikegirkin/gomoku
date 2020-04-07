enablePlugins(SbtTwirl)

name := """gomoku"""
organization := "net.girkin"

version := "1.0-SNAPSHOT"

scalaVersion := "2.13.1"

val http4sVersion = "0.21.2"
val circeVersion = "0.12.2"
val LogbackVersion = "1.2.3"
val zioVersion = "1.0.0-RC18-2"

libraryDependencies ++= Seq(
  "org.playframework.anorm" %% "anorm"      % "2.6.5",
  "org.postgresql"           % "postgresql" % "42.2.4",
  "com.mchange"              % "c3p0"       % "0.9.5.2",

  "org.http4s"      %% "http4s-dsl"            % http4sVersion,
  "org.http4s"      %% "http4s-blaze-client"   % http4sVersion,
  "org.http4s"      %% "http4s-blaze-server"   % http4sVersion,
  "org.http4s"      %% "http4s-circe"          % http4sVersion,
  "org.http4s"      %% "http4s-twirl"          % http4sVersion,

  "dev.zio"         %% "zio"                   % zioVersion,
  "dev.zio"         %% "zio-interop-cats"      % "2.0.0.0-RC12",

  "ch.qos.logback"   % "logback-classic"       % LogbackVersion,

  "io.circe"        %% "circe-generic"         % circeVersion,
//  "io.circe"        %% "circe-literal"         % circeVersion,
  "io.circe"        %% "circe-generic-extras"  % circeVersion,
  //"io.circe"        %% "circe-java8"           % circeVersion,
//  "io.circe"        %% "circe-shapes"          % circeVersion,
  "org.reactormonk" %% "cryptobits"            % "1.3",
  "com.pauldijou"   %% "jwt-circe"             % "4.3.0",

  "org.scalatest"   %% "scalatest"             % "3.1.1" % Test,
  "org.scalamock"   %% "scalamock"             % "4.4.0" % Test
)

// Adds additional packages into Twirl
//TwirlKeys.templateImports += "net.girkin.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "net.girkin.binders._"
