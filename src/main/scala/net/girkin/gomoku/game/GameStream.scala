package net.girkin.gomoku.game

import cats._
import cats.implicits._
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
) extends GomokuRequest

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
    ???
  }

  def processRequest(req: GomokuRequest): F[GomokuResponse] = {
    req match {
      case m @ MakeMove(id, userId, row, column) =>
        game.makeMove(MoveAttempt(row, column, userId)).fold(
          error => Monad[F].pure(
            BadRequest()
          ),
          newGameState => {
            saveMove(m)
            Monad[F].pure(
              StateChanged()
            )
          }
        )
      case Concede(userId) => ???
    }
  }
}