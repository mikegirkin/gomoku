package net.girkin.gomoku.api

import java.util.UUID

import cats.implicits._
import io.circe.syntax._
import fs2._
import fs2.concurrent.Queue
import net.girkin.gomoku.api.ApiObjects._
import net.girkin.gomoku.game.{GameServer, GameStore}
import net.girkin.gomoku.{AuthUser, Logging}
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.twirl._
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.{Close, Text}
import zio.interop.catz._
import zio.{RefM, Task}

class GameRoutesHandler (
  gameServer: GameServer,
  gameStore: GameStore,
  userChannels: RefM[Map[AuthUser, Queue[Task, WebSocketFrame]]]
) extends Http4sDsl[Task] with Logging {

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
      ok => Ok(ok.asJson)
    ).flatten
  }

  def handleSocketRequest(token: AuthUser): Task[Response[Task]] = {
    def findOrCreateOutboundPipe(token: AuthUser): Task[Stream[Task, WebSocketFrame]] = {
      for {
        channels <- userChannels.get
        outboundPipe <- if(!channels.contains(token)) {
            Task.succeed(channels(token))
          } else {
            Queue.unbounded[Task, WebSocketFrame]
          }
      } yield {
        outboundPipe.dequeue
      }
    }

    def processFrame(token: AuthUser)(frame: WebSocketFrame): Task[Map[AuthUser, WebSocketFrame]] = {
      frame match {
        case Text(msg, _) => for {
          allUserChannels <- userChannels.get
        } yield {
          allUserChannels.filterKeys(key => key != token).map {
            case (user, _) => user -> Text(s"From $token, message: $msg")
          }
        }
        case Close(_) => for {
          allUserChannels <- userChannels.get
          _ <- userChannels.update(m => Task.succeed(m.filterKeys(key => key != token)))
        } yield {
          allUserChannels.filterKeys(key => key != token).map {
            case (user, _) => user -> Text(s"User $token left the room")
          }
        }
        case _ => Task.succeed(Map.empty)
      }
    }

    def executeSendOrder(sendOrder: Map[AuthUser, WebSocketFrame]): Task[Unit] = {
      for {
        channels <- userChannels.get
        result <- sendOrder.map { case (user, frame) =>
          channels.get(user).map { queue => queue.enqueue1(frame) }
            .getOrElse(Task.succeed[Unit](()))
        }.toList.sequence.map(_ => ())
      } yield {
        result
      }
    }

    def inboundStream(token: AuthUser)(stream: Stream[Task, WebSocketFrame]): Stream[Task, Unit] = {
      stream.evalMap(processFrame(token)).evalMap(executeSendOrder)
    }

    for {
      outboundStream <- findOrCreateOutboundPipe(token)
      result <- {
        WebSocketBuilder[Task].build(outboundStream, inboundStream(token))
      }
    } yield {
      result
    }
  }
}

