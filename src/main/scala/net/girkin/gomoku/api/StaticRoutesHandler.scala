package net.girkin.gomoku.api

import java.io.File
import java.nio.file.Paths

import cats.effect.{Blocker, ContextShift, Effect}
import net.girkin.gomoku.Logging
import org.http4s.dsl.Http4sDsl
import org.http4s.{HttpRoutes, Response, StaticFile}

import scala.concurrent.ExecutionContext


class StaticRoutesHandler[F[_]: Effect : ContextShift](ec: ExecutionContext) extends Http4sDsl[F] with Logging {
  val service = HttpRoutes.of[F] {
    case GET -> file => serveStatic(file.toList.mkString("/"))
  }

  def serveStatic(file: String): F[Response[F]] = {
    val currentDir = Paths.get(System.getProperty("user.dir"))
    StaticFile.fromFile(
      new File(
        Paths.get(currentDir.toString, "static", file).toString
      ),
      Blocker.liftExecutionContext(ec)
    ).getOrElseF {
      val path =Paths.get(System.getProperty("user.dir"), "static", file).toString
      logger.warn(s"Failed to load file $file from $path")
      NotFound()
    }
  }
}
