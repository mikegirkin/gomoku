package net.girkin.gomoku.api

import java.util.UUID

import cats.implicits._
import io.circe.syntax._
import fs2._
import net.girkin.gomoku.api.ApiObjects._
import net.girkin.gomoku.game.{GameConcierge, GameStore}
import net.girkin.gomoku.{AuthUser, FunctionalLogging}
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.twirl._
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.{Close, Text}
import zio.Task
import zio.interop.catz._
import zio._



class GameRoutesHandler (
  concierge: GameConcierge,
  gameStore: GameStore,
  userChannels: OutboundChannels
) extends Http4sDsl[Task] with FunctionalLogging {

  private case class OutboundWebsocketMessage(
    user: AuthUser,
    frame: WebSocketFrame
  )

  sealed trait WebSocketFrameHandlingError extends Product with Serializable
  object WebSocketFrameHandlingError {
    case class BadRequest() extends WebSocketFrameHandlingError
    case class InternalException(exception: Throwable) extends WebSocketFrameHandlingError
  }


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
    gameStore.getGame(gameId).foldM(
      { error => InternalServerError() },
      gameOpt => gameOpt.fold(
        NotFound()
      ) {
        game => Ok(game.asJson)
      })
  }

  def joinRandomGame(token: AuthUser): Task[Response[Task]] = {
    concierge.joinRandomGame(token.userId).fold[Task[Response[Task]]](
      error => BadRequest(""),
      ok => Ok(ok.asJson)
    ).flatten
  }

  private def handleTextFrame(token: AuthUser, frame: Text): Task[List[OutboundWebsocketMessage]] = {
    val msg =  frame.str
    val result: IO[WebSocketFrameHandlingError, List[OutboundWebsocketMessage]] = for {
      allUsers <- userChannels.activeUsers()
      _ <- info(s"Received ${msg} from $token")
    } yield {
      allUsers.filterNot(user => user == token).map {
        user => OutboundWebsocketMessage(
          user,
          Text(s"From $token, message: $msg")
        )
      }
    }

    result.mapError {
      case WebSocketFrameHandlingError.BadRequest() =>
        List(OutboundWebsocketMessage(token, Text("Bad request")))
      case WebSocketFrameHandlingError.InternalException(exc) =>
        List(OutboundWebsocketMessage(token, Text(s"Internal server error ${exc}")))
    }.merge
  }

  def handleSocketRequest(token: AuthUser): Task[Response[Task]] = {
    def processFrame(token: AuthUser)(frame: WebSocketFrame): Task[List[OutboundWebsocketMessage]] = {
      frame match {
        case frame @ Text(_, _) => handleTextFrame(token, frame)
        case Close(_) => for {
          _ <- userChannels.removeOutboundUserChannel(token)
          allUsers <- userChannels.activeUsers()
          result = allUsers.filterNot(user => user == token).map { user =>
            OutboundWebsocketMessage(
              user,
              Text(s"User $token left the room")
            )
          }
        } yield {
          result
        }
        case _ => Task.succeed(List.empty)
      }
    }

    def executeSendOrder(sendOrder: List[OutboundWebsocketMessage]): Task[Unit] = {
      debug(s"Executing send order ${sendOrder}")
      sendOrder.map(m => userChannels.route(m.user, m.frame))
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


