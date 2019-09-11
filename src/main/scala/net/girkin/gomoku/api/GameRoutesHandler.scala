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
        allUserChannels <- userChannels.get
        existingOutboundOpt = allUserChannels.get(token)
        outboundQueue <- existingOutboundOpt.fold(
          Queue.unbounded[Task, WebSocketFrame]
        )(
          existing => Task.succeed(existing)
        )
        newOutbountQueuesList <- userChannels.update {
          channels => Task.succeed(channels + (token -> outboundQueue))
        }
      } yield {
        logger.debug(s"New outbound queues list ${newOutbountQueuesList}")
        outboundQueue.dequeue
      }
    }

    def processFrame(token: AuthUser)(frame: WebSocketFrame): Task[List[(AuthUser, WebSocketFrame)]] = {
      frame match {
        case Text(msg, _) => for {
          allUserChannels <- userChannels.get
        } yield {
          logger.debug(s"Received ${msg} from $token")
          logger.debug(s"All user channels: ${allUserChannels}")
          allUserChannels.filterKeys(key => key != token).toList.map {
            case (user, _) => user -> Text(s"From $token, message: $msg")
          }
        }
        case Close(_) => for {
          allUserChannels <- userChannels.get
          _ <- userChannels.update(m => Task.succeed(m.filterKeys(key => key != token)))
        } yield {
          allUserChannels.filterKeys(key => key != token).toList.map {
            case (user, _) => user -> Text(s"User $token left the room")
          }
        }
        case _ => Task.succeed(List.empty)
      }
    }

    def executeSendOrder(sendOrder: List[(AuthUser, WebSocketFrame)]): Task[Unit] = {
      logger.debug(s"Executing send order ${sendOrder}")
      for {
        channels <- userChannels.get
        result <- sendOrder.map { case (user, frame) =>
          channels.get(user).map { queue => queue.enqueue1(frame) }
            .getOrElse(Task.succeed[Unit](()))
        }.sequence.map(_ => ())
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

