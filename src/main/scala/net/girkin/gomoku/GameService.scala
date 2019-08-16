package net.girkin.gomoku

import java.util.UUID

import io.circe.syntax._
import fs2._
import fs2.concurrent.Queue
import net.girkin.gomoku.api.ApiObjects._
import net.girkin.gomoku.game.{GameServer, GameStore, JoinGameSuccess}
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.twirl._
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.Text
import zio.Task
import zio.interop.catz._

class GameService (
  gameServer: GameServer,
  gameStore: GameStore
) extends Http4sDsl[Task] {

  def gameApp(userToken: AuthUser): Task[Response[Task]] = {
    Ok(
      views.html.dashboard()
    )
  }

  def game(userToken: AuthUser, gameId: UUID): Task[Response[Task]] = {
    gameStore.getGame(gameId).flatMap(_.fold(
      NotFound()
    ) {
      game => Ok(game.asJson)
    })
  }

  def joinRandomGame(token: AuthUser): Task[Response[Task]] = {
    gameServer.joinRandomGame(token.userId).fold[Task[Response[Task]]](
      error => BadRequest(""),
      {
        case JoinGameSuccess(gameID, webSocketUrl) =>
          Ok(Map(
            "gameId" -> gameID.toString,
            "webSocketUrl" -> webSocketUrl
          ).asJson)
      }
    ).flatten
  }

  def wsGame(token: AuthToken, gameId: UUID): Task[Response[Task]] = {
    val echoReply: Pipe[Task, WebSocketFrame, WebSocketFrame] =
      _.collect {
        case Text(msg, _) => Text("You sent the server: " + msg)
        case _ => Text("Something new")
      }

    Queue
      .unbounded[Task, WebSocketFrame]
      .flatMap { q =>
        val d = q.dequeue.through(echoReply)
        val e = q.enqueue
        WebSocketBuilder[Task].build(d, e)
      }
  }
}

