package net.girkin.gomoku.api

import java.util.UUID

import cats.implicits._
import io.circe.syntax._
import fs2._
import net.girkin.gomoku.api.ApiObjects._
import net.girkin.gomoku.game.{GameConcierge, GameStore}
import net.girkin.gomoku.{AuthUser, Logging}
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.twirl._
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.{Close, Text}
import zio.Task
import zio.interop.catz._

class GameRoutesHandler (
  gameServer: GameConcierge,
  gameStore: GameStore,
  userChannels: OutboundChannels
) extends Http4sDsl[Task] with Logging {

  def gameApp(userToken: AuthUser): Task[Response[Task]] = {
    Ok(
      views.html.dashboard()
    )
  }

  def game(userToken: AuthUser, gameId: UUID): Task[Response[Task]] = {
    gameStore.getGame(gameId).foldM(
      { error => InternalServerError() },
      gameOpt => gameOpt.fold(
        NotFound()
      ) {
        game => Ok(game.asJson)
      })
  }

  def joinRandomGame(token: AuthUser): Task[Response[Task]] = {
    gameServer.joinRandomGame(token.userId).fold[Task[Response[Task]]](
      error => BadRequest(""),
      ok => Ok(ok.asJson)
    ).flatten
  }

  def handleSocketRequest(token: AuthUser): Task[Response[Task]] = {
    def processFrame(token: AuthUser)(frame: WebSocketFrame): Task[List[(AuthUser, WebSocketFrame)]] = {
      frame match {
        case Text(msg, _) => for {
          allUsers <- userChannels.activeUsers()
        } yield {
          logger.debug(s"Received ${msg} from $token")
          logger.debug(s"All user channels: ${allUsers}")
          allUsers.filterNot(user => user == token).map {
            user => user -> Text(s"From $token, message: $msg")
          }
        }
        case Close(_) => for {
          _ <- userChannels.removeOutboundUserChannel(token)
          allUsers <- userChannels.activeUsers()
        } yield {
          allUsers.filterNot(user => user == token).map {
            user => user -> Text(s"User $token left the room")
          }
        }
        case _ => Task.succeed(List.empty)
      }
    }

    def executeSendOrder(sendOrder: List[(AuthUser, WebSocketFrame)]): Task[Unit] = {
      logger.debug(s"Executing send order ${sendOrder}")
      sendOrder.map((userChannels.route _).tupled)
        .sequence
        .map { _ => () }
    }

    def inboundStream(token: AuthUser)(stream: Stream[Task, WebSocketFrame]): Stream[Task, Unit] = {
      stream.evalMap(processFrame(token)).evalMap(executeSendOrder)
    }

    for {
      outboundQueue <- userChannels.findOrCreateUserChannel(token)
      result <- {
        WebSocketBuilder[Task].build(outboundQueue.dequeue, inboundStream(token))
      }
    } yield {
      result
    }
  }
}


