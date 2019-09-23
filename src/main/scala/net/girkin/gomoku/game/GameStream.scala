package net.girkin.gomoku.game

import java.util.UUID

import fs2.{Pipe, Stream}
import fs2._
import net.girkin.gomoku.game.GomokuResponse.{BadMoveRequest, StateChanged}
import zio.{RefM, Task}

sealed trait GomokuRequest {
  def userId: UUID
  def gameId: UUID
}

case class MakeMove(
  id: UUID,
  userId: UUID,
  gameId: UUID,
  row: Int,
  column: Int
) extends GomokuRequest

case class Concede(
  userId: UUID,
  gameId: UUID
) extends GomokuRequest

sealed trait GomokuResponse

object GomokuResponse {
  case class StateChanged() extends GomokuResponse
  case class BadMoveRequest(error: MoveError) extends GomokuResponse
}

class GameStream(gameRef: RefM[Game]) {

  def saveMove(move: MakeMove): Task[GomokuResponse] = {
    ???
  }

  private def handleMakeMove(attempt: MakeMove): Task[List[GomokuResponse]] = {
    for {
      game <- gameRef.get
      result <- game.makeMove(MoveAttempt(attempt.row, attempt.column, attempt.userId)).fold(
        error => Task.succeed(List(BadMoveRequest(error))),
        newGameState => {
          for {
            _ <- saveMove(attempt)
            _ <- gameRef.set(newGameState)
          } yield {
            List(StateChanged())
          }
        }
      )
    } yield {
      result
    }
  }

  def processRequest(req: GomokuRequest): Task[List[GomokuResponse]] = {
    req match {
      case m @ MakeMove(_, _, _, _, _) => handleMakeMove(m)
      case Concede(userId, gameId) => ???
    }
  }

  def buildStream(): Pipe[Task, GomokuRequest, GomokuResponse] = {
    (in: Stream[Task, GomokuRequest]) => {
      in.evalMap(processRequest).flatMap(Stream.emits)
    }
  }
}