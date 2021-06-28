package net.girkin.gomoku.api

import io.circe.syntax._
import net.girkin.gomoku.api.ApiObjects._
import net.girkin.gomoku.game.{GameConcierge, GameStore}
import net.girkin.gomoku.{AuthUser, FunctionalLogging}
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.twirl._
import zio.Task
import zio.interop.catz._

import java.util.UUID

sealed trait IncomingGameMessage
object IncomingGameMessage {
  final case object RequestJoinGame extends IncomingGameMessage
  final case object RequestLeaveGame extends IncomingGameMessage
  final case class RequestMove(row: Int, col: Int) extends IncomingGameMessage
}

class GameRoutesHandler(
    concierge: GameConcierge,
    gameStore: GameStore,
    userChannels: OutboundChannels
) extends Http4sDsl[Task]
    with FunctionalLogging {

  def listChannels(token: AuthUser): Task[Response[Task]] = {
    for {
      channels <- userChannels.list()
      channesData = channels.map {
        case (user, queue) => s"User: ${user.userId}"
      }.toList
      html <- Ok(
        views.html.debug.channels(channesData)
      )
    } yield {
      html
    }
  }

  def gameApp(userToken: AuthUser): Task[Response[Task]] = {
    Ok(
      views.html.dashboard()
    )
  }

  def game(userToken: AuthUser, gameId: UUID): Task[Response[Task]] = {
    gameStore
      .getGame(gameId)
      .foldM(
        { error => InternalServerError() },
        gameOpt =>
          gameOpt.fold(
            NotFound()
          ) { game =>
            Ok(game.asJson)
          }
      )
  }

  @deprecated
  def joinRandomGame(token: AuthUser): Task[Response[Task]] = {
    concierge
      .joinRandomGame(token.userId)
      .fold[Task[Response[Task]]](
        error => BadRequest(""),
        ok => Ok(ok.asJson)
      )
      .flatten
  }

  def handleSocketRequest(token: AuthUser): Task[Response[Task]] = {
    for {
      outboundQueue <- userChannels.findOrCreateUserChannel(token)
      handler = new GameWebSocketStreamHandler(concierge, userChannels)
      result <- {
        WebSocketBuilder[Task].build(
          outboundQueue.dequeue,
          handler.gameWebSocketPipe(token, userChannels) andThen { _.map(_ => ()) }
        )
      }
    } yield {
      result
    }
  }
}
