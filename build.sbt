addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.7")
addCompilerPlugin("com.olegpy"     %% "better-monadic-for" % "0.2.4")

enablePlugins(SbtTwirl)

scalacOptions += "-Ypartial-unification"

name := """gomoku"""
organization := "net.girkin"

version := "1.0-SNAPSHOT"

scalaVersion := "2.12.6"

val http4sVersion = "0.20.1"
val circeVersion = "0.11.1"
val LogbackVersion = "1.2.3"

libraryDependencies ++= Seq(
  "org.playframework.anorm" %% "anorm"      % "2.6.2",
  "org.postgresql"           % "postgresql" % "42.2.4",
  "com.mchange"              % "c3p0"       % "0.9.5.2",

  "org.http4s"      %% "http4s-dsl"            % http4sVersion,
  "org.http4s"      %% "http4s-blaze-client"   % http4sVersion,
  "org.http4s"      %% "http4s-blaze-server"   % http4sVersion,
  "org.http4s"      %% "http4s-circe"          % http4sVersion,
  "org.http4s"      %% "http4s-twirl"          % http4sVersion,

  "dev.zio"         %% "zio"                   % "1.0.0-RC8-12",
  "dev.zio"         %% "zio-interop-cats"      % "1.0.0-RC8-12",

  "ch.qos.logback"   % "logback-classic"       % LogbackVersion,

  "io.circe"        %% "circe-generic"         % circeVersion,
//  "io.circe"        %% "circe-literal"         % circeVersion,
  "io.circe"        %% "circe-generic-extras"  % circeVersion,
  "io.circe"        %% "circe-java8"           % circeVersion,
//  "io.circe"        %% "circe-shapes"          % circeVersion,
  "org.reactormonk" %% "cryptobits"            % "1.2",
  "com.pauldijou"   %% "jwt-circe"             % "2.1.0",

  "org.scalatest"   %% "scalatest"             % "3.0.5" % "test"
)

// Adds additional packages into Twirl
//TwirlKeys.templateImports += "net.girkin.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "net.girkin.binders._"
