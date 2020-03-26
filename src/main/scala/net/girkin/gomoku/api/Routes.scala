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

class Routes(
  authService: Auth[Task],
  gameService: GameRoutesHandler,
) extends Http4sDsl[Task] {

  val gameRoutes = authService.secured(
    AuthedRoutes.of[AuthUser, Task] {
      case GET -> Root as token => gameService.gameApp(token)
      case GET -> Root / UUIDVar(gameId) as token => gameService.game(token, gameId)
      case POST -> Root / "join" as token => gameService.joinRandomGame(token)
    }
  )

  val webSocketRoutes = authService.secured(
    AuthedRoutes.of[AuthUser, Task] {
      case GET -> Root / "ws" as token => gameService.handleSocketRequest(token)
    }
  )

  val service = webSocketRoutes orElse gameRoutes
}
