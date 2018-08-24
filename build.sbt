scalacOptions += "-Ypartial-unification"

name := """gomoku"""
organization := "net.girkin"

version := "1.0-SNAPSHOT"

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .disablePlugins(PlayLayoutPlugin)

PlayKeys.playMonitoredFiles ++= (sourceDirectories in (Compile, TwirlKeys.compileTemplates)).value

scalaVersion := "2.12.6"

val http4sVersion = "0.18.15"

libraryDependencies ++= Seq(
  guice,
  jdbc,

  "org.playframework.anorm" %% "anorm"      % "2.6.2",
  "org.postgresql"           % "postgresql" % "42.2.4",

  "org.http4s"             %% "http4s-dsl"          % http4sVersion,
  "org.http4s"             %% "http4s-blaze-client" % http4sVersion,
  "org.http4s"             %% "http4s-blaze-server" % http4sVersion,
  "org.http4s"             %% "http4s-circe"        % http4sVersion,

  "io.circe"      %% "circe-generic"         % "0.9.3",
  "io.circe"      %% "circe-literal"         % "0.9.3",
  "io.circe"      %% "circe-generic-extras"  % "0.9.3",
  "io.circe"      %% "circe-java8"           % "0.9.3",
  "com.pauldijou" %% "jwt-circe"             % "0.17.0",

  "org.scalatestplus.play" %% "scalatestplus-play"  % "3.1.2" % Test
)

// Adds additional packages into Twirl
//TwirlKeys.templateImports += "net.girkin.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "net.girkin.binders._"
