package net.girkin.gomoku.api

import java.util.UUID

import cats.implicits._
import net.girkin.gomoku.{Auth, AuthUser}
import org.http4s.AuthedRoutes
import org.http4s.dsl.Http4sDsl
import zio.Task
import zio.interop.catz._

import scala.util.Try

class ArbitratyPathVar[A](cast: String => A) {
  def unapply(str: String): Option[A] =
    if (!str.isEmpty)
      Try(cast(str)).toOption
    else
      None
}

object UUIDVar extends ArbitratyPathVar[UUID](s => UUID.fromString(s))

class GameRoutes(
  authService: Auth[Task],
  gameRoutesHandler: GameRoutesHandler,
) extends Http4sDsl[Task] {

  val gameRoutes = authService.secured(
    AuthedRoutes.of[AuthUser, Task] {
      case GET -> Root / UUIDVar(gameId) as token => gameRoutesHandler.game(token, gameId)
      case GET -> Root as token => gameRoutesHandler.gameApp(token)
    }
  )

  val webSocketRoutes = authService.secured(
    AuthedRoutes.of[AuthUser, Task] {
      case GET -> Root / "ws" as token => gameRoutesHandler.handleSocketRequest(token)
    }
  )

  val service = webSocketRoutes combineK gameRoutes
}

