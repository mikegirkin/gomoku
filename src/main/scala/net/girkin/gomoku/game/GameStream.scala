package net.girkin.gomoku.game

import cats._
import cats.implicits._
import java.util.UUID

import cats.Monad
import net.girkin.gomoku.game.GomokuResponse.{BadMoveRequest, StateChanged}

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

object GomokuResponse {
  case class StateChanged() extends GomokuResponse
  case class BadMoveRequest() extends GomokuResponse
}

class GameStream[F[_]: Monad] {

  def saveMove(move: MakeMove): F[Unit] = {
    ???
  }

  def processRequest(game: Game)(req: GomokuRequest): F[(Game, List[GomokuResponse])] = {
    req match {
      case m @ MakeMove(id, userId, row, column) =>
        game.makeMove(MoveAttempt(row, column, userId)).fold(
          error => Monad[F].pure(
            game -> List(BadMoveRequest())
          ),
          newGameState => {
            for {
              _ <- saveMove(m)
            } yield {
              newGameState -> List(StateChanged())
            }
          }
        )
      case Concede(userId) => ???
    }
  }
}