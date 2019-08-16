package net.girkin.gomoku

import java.util.UUID

import org.http4s.AuthedService
import org.http4s.dsl.Http4sDsl
import zio.Task
import zio.interop.catz._
import zio.interop.catz.implicits._
import cats.implicits._

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
  gameService: GameService,
) extends Http4sDsl[Task] {

  val gameRoutes = authService.secured(
    AuthedService[AuthUser, Task] {
      case GET -> Root as token => gameService.gameApp(token)
      case GET -> Root / UUIDVar(gameId) as token => gameService.game(token, gameId)
      case POST -> Root / "join" as token => gameService.joinRandomGame(token)
    }
  )

  val webSocketRoutes = authService.secured(
    AuthedService[AuthUser, Task] {
      case GET -> Root / "ws" / UUIDVar(gameId) as token => gameService.wsGame(token, gameId)
    }
  )

  val service = webSocketRoutes <+> gameRoutes
}
