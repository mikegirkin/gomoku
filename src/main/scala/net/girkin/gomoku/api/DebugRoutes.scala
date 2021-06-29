package net.girkin.gomoku.api

import net.girkin.gomoku.game.GameStore
import net.girkin.gomoku.{Auth, AuthUser}
import org.http4s.AuthedRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.twirl._
import zio.Task
import zio.interop.catz._

class DebugRoutes(
  authService: Auth[Task],
  outboundChannels: OutboundChannels,
  gameStore: GameStore
) extends Http4sDsl[Task] {

  val debugRoutesHandler = new DebugRoutesHandler(outboundChannels, gameStore)

  val routes = authService.secured(AuthedRoutes.of[AuthUser, Task] {
    case GET -> Root / "games" as token =>
      debugRoutesHandler.listGames().foldM(
        { result =>
          Ok(result.toString)
        },
        { error =>
          InternalServerError(error.toString)
        }
      )
    case GET -> Root / "channels" as token =>
      debugRoutesHandler.listChannels(token).flatMap { r =>
        Ok(views.html.debug.channels(r))
      }
  })
}
