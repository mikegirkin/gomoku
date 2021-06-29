package net.girkin.gomoku.api

import io.circe.parser.decode
import io.circe.syntax._
import cats.data.Kleisli
import fs2.{Pipe, Stream}
import net.girkin.gomoku.{AuthUser, FunctionalLogging}
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.{Close, Text}
import zio.{IO, Task, ZIO}
import zio.interop.catz._
import ApiObjects._
import net.girkin.gomoku.game.{GameConcierge, JoinGameSuccess}

import java.util.UUID

case class OutboundWebsocketMessage(
  destinationUser: AuthUser,
  frame: WebSocketFrame
)

sealed trait WebSocketFrameHandlingError extends Product with Serializable
object WebSocketFrameHandlingError {
  case class BadRequest() extends WebSocketFrameHandlingError
  case class InternalException(exception: Throwable) extends WebSocketFrameHandlingError
}

class GameWebSocketStreamHandler(
  concierge: GameConcierge,
  userChannels: OutboundChannels
) extends FunctionalLogging {

  private def handleTextFrame(token: AuthUser, frame: Text): Task[List[OutboundWebsocketMessage]] = {
    val deserializeFrame: Kleisli[IO[WebSocketFrameHandlingError, *], Text, IncomingGameMessage] = Kleisli { frame =>
      ZIO
        .fromEither(
          decode[IncomingGameMessage](frame.str)
        )
        .mapError(_ => WebSocketFrameHandlingError.BadRequest())
    }

    val handleIncomingMessage: Kleisli[IO[WebSocketFrameHandlingError, *], IncomingGameMessage, List[OutboundWebsocketMessage]] = Kleisli {
      case IncomingGameMessage.RequestJoinGame =>
        concierge
          .joinRandomGame(token.userId)
          .fold(
            { joinGameError =>
              val frame = WebSocketFrame.Text(joinGameError.asJson.toString())
              List(OutboundWebsocketMessage(token, frame))
            },
            { joinGameSuccess =>
              val frame = WebSocketFrame.Text(joinGameSuccess.asJson.toString())
              joinGameSuccess match {
                case JoinGameSuccess.JoinedQueue =>
                  List(OutboundWebsocketMessage(token, frame))
                case m @ JoinGameSuccess.JoinedGame(gameId, user1, user2) =>
                  List(
                    OutboundWebsocketMessage(AuthUser(user1), frame),
                    OutboundWebsocketMessage(AuthUser(user2), frame)
                  )
              }
            }
          )

      case IncomingGameMessage.RequestLeaveGame      => ??? //find active game, request leave
      case IncomingGameMessage.RequestMove(row, col) => ??? // find active game, pass move request to it
    }

    val result = (deserializeFrame andThen handleIncomingMessage).run(frame)

    result.mapError {
      case WebSocketFrameHandlingError.BadRequest() =>
        List(OutboundWebsocketMessage(token, Text("Bad request")))
      case WebSocketFrameHandlingError.InternalException(exc) =>
        List(OutboundWebsocketMessage(token, Text(s"Internal server error $exc")))
    }.merge
  }

  private def handleCloseFrame(token: AuthUser, frame: Close): Task[List[OutboundWebsocketMessage]] = {
    for {
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
  }

  def processFrame(token: AuthUser)(frame: WebSocketFrame): Task[List[OutboundWebsocketMessage]] = {
    frame match {
      case frame @ Text(_, _) => handleTextFrame(token, frame)
      case frame @ Close(_) => handleCloseFrame(token, frame)
      case _ => Task.succeed(List.empty)
    }
  }

  private def executeSendOrder(sendOrder: OutboundWebsocketMessage): Task[Unit] = {
    for {
      _ <- debug(s"Executing send order $sendOrder")
      result <- userChannels.route(sendOrder.destinationUser, sendOrder.frame)
    } yield {
      result
    }
  }

  def gameWebSocketPipe(token: AuthUser): Pipe[Task, WebSocketFrame, OutboundWebsocketMessage] = { stream =>
    stream
      .evalMap(processFrame(token))
      .flatMap(Stream.emits)
      .evalTap(executeSendOrder)
  }
}
