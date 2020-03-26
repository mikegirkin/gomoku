package net.girkin.gomoku.api

import fs2.concurrent.Queue
import net.girkin.gomoku.{AuthUser, FunctionalLogging}
import org.http4s.websocket.WebSocketFrame
import zio.{IO, RefM, Task, UIO}
import zio.interop.catz._

final case class NoSuchUserChannel(user: AuthUser) extends Exception

class OutboundChannels(
  private val userChannels: RefM[Map[AuthUser, Queue[Task, WebSocketFrame]]]
) extends FunctionalLogging[Task] {

  def route(user: AuthUser, message: WebSocketFrame): Task[Unit] = {
    for {
      _ <- debug(s"Sending message to ${user}")
      channels <- userChannels.get
      result <- channels.get(user).map { queue => queue.enqueue1(message) }
          .getOrElse(IO.fail(NoSuchUserChannel(user)))
    } yield {
      result
    }
  }

  def findOrCreateUserChannel(user: AuthUser): Task[Queue[Task, WebSocketFrame]] = {
    for {
      allUserChannels <- userChannels.get
      existingOutboundOpt = allUserChannels.get(user)
      outboundQueue <- existingOutboundOpt.fold(
        Queue.unbounded[Task, WebSocketFrame]
      )(
        existing => Task.succeed(existing)
      )
      newOutboundQueuesList <- userChannels.update {
        channels => Task.succeed(channels + (user -> outboundQueue))
      }
      _ <- debug(s"New outbound queues list ${newOutboundQueuesList}")
    } yield {
      outboundQueue
    }
  }

  def removeOutboundUserChannel(user: AuthUser): Task[Unit] = {
    for {
      _ <- userChannels.update(m => Task.succeed(m.view.filterKeys(key => key != user).toMap))
    } yield {
      ()
    }
  }

  def activeUsers(): UIO[List[AuthUser]] = {
    for {
      channels <- userChannels.get
    } yield {
      channels.map { case (user, _) => user }.toList
    }
  }
}

object OutboundChannels {
  def make(): Task[OutboundChannels] = {
    for {
      userChannelsStore <- zio.RefM.make(Map.empty[AuthUser, fs2.concurrent.Queue[Task, WebSocketFrame]])
    } yield {
      new OutboundChannels(userChannelsStore)
    }
  }
}
