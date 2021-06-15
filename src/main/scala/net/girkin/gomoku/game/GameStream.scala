package net.girkin.gomoku.game

import java.util.UUID
import fs2.{Pipe, Stream}
import zio.{IO, RefM, UIO}

sealed trait GomokuRequest {
  def userId: UUID
  def gameId: UUID
}

object GomokuRequest {
  final case class MakeMove(
    id: UUID,
    userId: UUID,
    gameId: UUID,
    row: Int,
    column: Int
  ) extends GomokuRequest

  final case class Concede(
    userId: UUID,
    gameId: UUID
  ) extends GomokuRequest

  def makeMove(id: UUID, userId: UUID, gameId: UUID, row: Int, column: Int): GomokuRequest =
    MakeMove(id, userId, gameId, row, column)

  def concede(userId: UUID, gameId: UUID): GomokuRequest = Concede(userId, gameId)
}


sealed trait GomokuResponse
object GomokuResponse {
  final case class StateChanged(gameId: UUID) extends GomokuResponse

  def stateChanged(gameId: UUID): GomokuResponse = StateChanged(gameId)
}

sealed trait GomokuError
object GomokuError {
  final case class GameIsFull private (gameId: UUID) extends GomokuError
  final case class PlayerPresent private (gameId: UUID, userId: UUID) extends GomokuError
  final case class BadMoveRequest private (error: MoveError, gameId: UUID, userId: UUID) extends GomokuError
  final case class InternalError private (reason: String) extends GomokuError
  final case class BadRequest private (gameId: UUID, reason: String) extends GomokuError

  def badMoveRequest(error: MoveError, gameId: UUID, userId: UUID): GomokuError = BadMoveRequest(error, gameId, userId)
  def internalError(reason: String): GomokuError = InternalError(reason)
  def gameIsFull(gameId: UUID): GomokuError = GameIsFull(gameId)
  def playerPresent(gameId: UUID, userId: UUID): GomokuError = PlayerPresent(gameId, userId)
  def badRequest(gameId: UUID, reason: String): GomokuError = BadRequest(gameId, reason)
}

/***
 * This class represents a stream of requests/responses that goes through a particular game instance
 * This stream is intended to exist only when the game exist in memory
 * @param gameStore
 * @param gameRef
 */
class GameStream private (
  gameStore: GameStore,
  gameRef: RefM[Game],
  val gameId: UUID
) {

  import GomokuRequest._
  import GomokuError._
  import GomokuResponse._

  def getGame: UIO[Game] = {
    gameRef.get
  }

  private def handleMakeMove(command: MakeMove): IO[GomokuError, GomokuResponse] = {

    def updateGameAndSaveMove(current: Game): IO[GomokuError, Game] = {
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

    gameRef.updateAndGet(updateGameAndSaveMove).map { newGameState =>
      stateChanged(newGameState.gameId)
    }
  }

  private def handleConcede(request: Concede): IO[GomokuError, GomokuResponse] = {
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
        GomokuError.badRequest(
          game.gameId,
          reason = s"User ${request.userId} is not in the game ${game.gameId}"
        )
      }
    }
  }

  private def processRequest(req: GomokuRequest): IO[GomokuError, GomokuResponse] = {
    req match {
      case m @ MakeMove(_, _, _, _, _) => handleMakeMove(m)
      case m @ Concede(_, _) => handleConcede(m)
    }
  }

  val pipe: Pipe[UIO, GomokuRequest, Either[GomokuError, GomokuResponse]] = {
    (in: Stream[UIO, GomokuRequest]) => {
      in.evalMap( x =>
        processRequest(x).either
      ).flatMap(
        result => Stream.emit(result)
      )
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