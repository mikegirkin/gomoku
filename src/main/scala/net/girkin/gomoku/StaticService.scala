package net.girkin.gomoku

import java.io.File
import java.nio.file.Paths

import cats.effect.Effect
import org.http4s.dsl.Http4sDsl
import org.http4s.{HttpService, Response, StaticFile}

class StaticService[F[_]: Effect] extends Http4sDsl[F] with Logging {
  val service = HttpService[F] {
    case GET -> file => serveStatic(file.toList.mkString("/"))
  }

  def serveStatic(file: String): F[Response[F]] = {
    val currentDir = Paths.get(System.getProperty("user.dir"))
    StaticFile.fromFile(
      new File(
        Paths.get(currentDir.toString, "static", file).toString
      )
    ).getOrElseF {
      val path =Paths.get(System.getProperty("user.dir"), "static", file).toString
      logger.warn(s"Failed to load file $file from $path")
      NotFound()
    }
  }
}
