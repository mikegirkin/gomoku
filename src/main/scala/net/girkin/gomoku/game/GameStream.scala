package net.girkin.gomoku.game

import java.util.UUID
import fs2.{Pipe, Stream}
import zio.{IO, RefM, UIO}

sealed trait GomokuGameRequest {
  def userId: UUID

  def gameId: UUID
}

object GomokuGameRequest {
  final case class MakeMove(
    id: UUID,
    userId: UUID,
    gameId: UUID,
    row: Int,
    column: Int
  ) extends GomokuGameRequest

  final case class Concede(
    userId: UUID,
    gameId: UUID
  ) extends GomokuGameRequest

  def makeMove(id: UUID, userId: UUID, gameId: UUID, row: Int, column: Int): GomokuGameRequest =
    MakeMove(id, userId, gameId, row, column)

  def concede(userId: UUID, gameId: UUID): GomokuGameRequest = Concede(userId, gameId)
}


sealed trait GomokuGameResponse

object GomokuGameResponse {
  final case class StateChanged(gameId: UUID) extends GomokuGameResponse
  def stateChanged(gameId: UUID): GomokuGameResponse = StateChanged(gameId)
}

sealed trait GomokuGameError

object GomokuGameError {
  final case class GameIsFull private(gameId: UUID) extends GomokuGameError
  final case class PlayerPresent private(gameId: UUID, userId: UUID) extends GomokuGameError
  final case class BadMoveRequest private(error: MoveError, gameId: UUID, userId: UUID) extends GomokuGameError
  final case class InternalError private(reason: String) extends GomokuGameError
  final case class BadRequest private(gameId: UUID, reason: String) extends GomokuGameError

  def badMoveRequest(error: MoveError, gameId: UUID, userId: UUID): GomokuGameError = BadMoveRequest(error, gameId, userId)

  def internalError(reason: String): GomokuGameError = InternalError(reason)

  def gameIsFull(gameId: UUID): GomokuGameError = GameIsFull(gameId)

  def playerPresent(gameId: UUID, userId: UUID): GomokuGameError = PlayerPresent(gameId, userId)

  def badRequest(gameId: UUID, reason: String): GomokuGameError = BadRequest(gameId, reason)
}

/** *
 * This class represents a stream of requests/responses that goes through a particular game instance
 * This stream is intended to exist only when the game exist in memory
 *
 * @param gameStore
 * @param gameRef
 */
class GameStream private(
  gameStore: GameStore,
  gameRef: RefM[Game],
  val gameId: UUID
) {

  import GomokuGameRequest._
  import GomokuGameError._
  import GomokuGameResponse._

  def handleMakeMove(command: MakeMove): IO[GomokuGameError, GomokuGameResponse] = {

    def updateGameAndSaveMove(current: Game): IO[GomokuGameError, Game] = {
      val move = MoveAttempt(command.row, command.column, command.userId)

      current.makeMove(move).fold(
        error => IO.fail(badMoveRequest(error, current.gameId, command.userId)),
        newGameState => {
          for {
            _ <- gameStore.saveMove(current, move).mapError(err => internalError(err.toString))
          } yield {
            newGameState
          }
        }
      )
    }

    gameRef.updateAndGet(updateGameAndSaveMove)
      .map { newGameState =>
        stateChanged(newGameState.gameId)
      }
  }

  def handleConcede(request: Concede): IO[BadRequest, GomokuGameResponse] = {
    gameRef.modify { game =>
      val newStateOpt = for {
        playerNumber <- game.getPlayerNumber(request.userId)
        newState = game.playerConceded(playerNumber)
      } yield {
        newState
      }

      IO.fromOption(
        newStateOpt.map { state =>
          StateChanged(state.gameId) -> state
        }
      ).mapError { _ =>
        GomokuGameError.BadRequest(
          game.gameId,
          reason = s"User ${request.userId} is not in the game ${game.gameId}"
        )
      }
    }
  }
}

object GameStream {
  def make(gameStore: GameStore, game: Game): UIO[GameStream] = for {
    gameRef <- RefM.make(game)
  } yield {
    new GameStream(gameStore, gameRef, game.gameId)
  }

  def makeWithEmptyGame(gameStore: GameStore, rules: GameRules, player1Id: UUID, player2Id: UUID): UIO[GameStream] = {
    make(gameStore, Game.create(rules, player1Id, player2Id))
  }
}