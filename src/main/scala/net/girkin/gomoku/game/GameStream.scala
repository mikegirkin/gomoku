package net.girkin.gomoku.game

import java.util.UUID
import fs2.{Pipe, Stream}
import net.girkin.gomoku.Constants
import zio.{IO, RefM, UIO}

sealed trait GomokuRequest {
  def userId: UUID
  def gameId: UUID
}

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

sealed trait GomokuResponse
object GomokuResponse {
  final case class StateChanged(gameId: UUID) extends GomokuResponse

  def stateChanged(gameId: UUID): GomokuResponse = StateChanged(gameId)
}

sealed trait GomokuError
object GomokuError {
  final case class GameIsFull(gameId: UUID) extends GomokuError
  final case class PlayerPresent(gameId: UUID, userId: UUID) extends GomokuError
  final case class BadMoveRequest(error: MoveError, gameId: UUID, userId: UUID) extends GomokuError
  final case class InternalError(reason: String) extends GomokuError

  def badMoveRequest(error: MoveError, gameId: UUID, userId: UUID): GomokuError = BadMoveRequest(error, gameId, userId)
  def internalError(reason: String): GomokuError = InternalError(reason)
  def gameIsFull(gameId: UUID): GomokuError = GameIsFull(gameId)
  def playerPresent(gameId: UUID, userId: UUID): GomokuError = PlayerPresent(gameId, userId)
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

  import GomokuError._
  import GomokuResponse._

  def getGame: UIO[Game] = {
    gameRef.get
  }

  def addPlayer(playerId: UUID): IO[GomokuError, Game] = {
    gameRef.updateAndGet { game =>
      if(game.hasBothPlayers) IO.fail(GomokuError.gameIsFull(game.gameId))
      else if(game.getPlayerNumber(playerId).isDefined) IO.fail(GomokuError.playerPresent(game.gameId, playerId))
      else {
        val updatedGame = game.addPlayer(playerId)
        IO.succeed(updatedGame)
      }
    }
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
    for {
      game <- gameRef.get
      newState = game.removePlayer(request.userId)
      _ <- gameRef.set(newState)
    } yield {
      StateChanged(game.gameId)
    }
  }

  private def processRequest(req: GomokuRequest): IO[GomokuError, GomokuResponse] = {
    req match {
      case m @ MakeMove(_, _, _, _, _) => handleMakeMove(m)
      case m @ Concede(_, _) => handleConcede(m)
    }
  }

  val stream: Pipe[IO[GomokuError, *], GomokuRequest, GomokuResponse] = {
    (in: Stream[IO[GomokuError, *], GomokuRequest]) => {
      in.evalMap(processRequest).flatMap(result => Stream.emit(result))
    }
  }
}

object GameStream {
  def make(gameStore: GameStore, game: Game): UIO[GameStream] = for {
    gameRef <- RefM.make(game)
  } yield {
    new GameStream(gameStore, gameRef, game.gameId)
  }

  def makeWithEmptyGame(gameStore: GameStore, rules: GameRules): UIO[GameStream] = {
    make(gameStore, Game.create(rules))
  }
}