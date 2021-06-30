addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.13.0" cross CrossVersion.full)
enablePlugins(SbtTwirl)

name := """gomoku"""
organization := "net.girkin"

version := "1.0-SNAPSHOT"

scalaVersion := "2.13.6"
scalacOptions ++= Seq("-deprecation", "-feature")

val http4sVersion = "0.21.3"
val circeVersion = "0.12.2"
val LogbackVersion = "1.2.3"
val zioVersion = "1.0.9"

libraryDependencies ++= Seq(
  "org.playframework.anorm" %% "anorm"          % "2.6.5",
  "org.playframework.anorm" %% "anorm-postgres" % "2.6.5",
  "org.postgresql"           % "postgresql"     % "42.2.12",
  "com.mchange"              % "c3p0"           % "0.9.5.2",

  "org.http4s"      %% "http4s-dsl"            % http4sVersion,
  "org.http4s"      %% "http4s-blaze-client"   % http4sVersion,
  "org.http4s"      %% "http4s-blaze-server"   % http4sVersion,
  "org.http4s"      %% "http4s-circe"          % http4sVersion,
  "org.http4s"      %% "http4s-twirl"          % http4sVersion,

  "dev.zio"         %% "zio"                   % zioVersion,
  "dev.zio"         %% "zio-interop-cats"      % "2.5.1.0",

  "ch.qos.logback"   % "logback-classic"       % LogbackVersion,

  "io.circe"        %% "circe-generic"         % circeVersion,
//  "io.circe"        %% "circe-literal"         % circeVersion,
  "io.circe"        %% "circe-generic-extras"  % circeVersion,
  //"io.circe"        %% "circe-java8"           % circeVersion,
//  "io.circe"        %% "circe-shapes"          % circeVersion,
  "org.reactormonk" %% "cryptobits"            % "1.3",
  "com.pauldijou"   %% "jwt-circe"             % "4.3.0",

  "org.scalatest"   %% "scalatest"              % "3.1.1" % Test,
  "org.scalamock"   %% "scalamock"              % "4.4.0" % Test,
  "org.http4s"      %% "http4s-jdk-http-client" % "0.3.7" % Test
)

parallelExecution in Test := false

// Adds additional packages into Twirl
//TwirlKeys.templateImports += "net.girkin.net.girkin.gomoku.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "net.girkin.binders._"
