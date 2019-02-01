package net.girkin.gomoku.game

import cats._, cats.implicits._
import java.util.UUID

import cats.Monad


sealed trait GomokuRequest

case class MakeMove(
  id: UUID,
  userId: UUID,
  row: Int,
  column: Int
) extends GomokuRequest

case class Concede(
  userId: UUID
)

sealed trait GomokuResponse

case class StateChanged() extends GomokuResponse
case class BadRequest() extends GomokuResponse

class GameStream[F[_]: Monad](game: Game) {
  def gameStream(
    incoming: fs2.Stream[F, GomokuRequest]
  ): fs2.Stream[F, GomokuResponse] = {
    ???
  }

  def saveMove(move: MakeMove): F[Unit] = {

  }

  def processRequest(req: GomokuRequest): F[GomokuResponse] = {
    req match {
      case MakeMove(id, userId, row, column) =>
        game.makeMove(MoveAttempt(row, column, userId)).fold(
          error => {
            BadRequest()
          },
          newGameState => {
            saveMove()
            StateChanged()
          }
        )
      case Concede(userId) => ???
    }
  }
}