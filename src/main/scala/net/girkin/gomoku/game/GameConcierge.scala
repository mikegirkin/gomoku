package net.girkin.gomoku.game

import java.util.UUID

import net.girkin.gomoku.AuthUser
import zio.IO

object Ruleset {
  val height = 5
  val width = 5
  val winningCondition = 4
}

case class JoinGameSuccess(
  gameId: UUID
)

case class LeaveGameSuccess(
  gameId: UUID
)

case class JoinGameError(
  reason: String
)

sealed trait LeaveGameError extends Product with Serializable
object LeaveGameError {
  case object NoSuchGameError extends LeaveGameError
  final case class StoreError(exception: Exception) extends LeaveGameError
}

trait GameConcierge {
  def joinRandomGame(userId: UUID): IO[JoinGameError, JoinGameSuccess]
  def leaveGame(user: AuthUser): IO[LeaveGameError, LeaveGameSuccess]
}

class GameConciergeImpl(
  gameStore: GameStore
) extends GameConcierge {

  def joinRandomGame(userId: UUID): IO[JoinGameError, JoinGameSuccess] = {
    val gameF = for {
      games <- gameStore.getGamesAwaitingPlayers()
      gameToAddPlayer = games.headOption.getOrElse(
        Game.create(Ruleset.height, Ruleset.width, Ruleset.winningCondition)
      )
      updatedGame = {
        if (gameToAddPlayer.players.contains(userId)) gameToAddPlayer
        else gameToAddPlayer.addPlayer(userId)
      }
      _ <- gameStore.saveGameRecord(updatedGame)
    } yield {
      JoinGameSuccess(updatedGame.gameId)
    }

    gameF.mapError(
      exc => JoinGameError(exc.exception.getMessage)
    )
  }

  override def leaveGame(user: AuthUser): IO[LeaveGameError, LeaveGameSuccess] = {
    val resultF: IO[LeaveGameError, LeaveGameSuccess] = for {
      gameOpt <- gameStore.getActiveGameForPlayer(user).mapError(storeError => LeaveGameError.StoreError(storeError.exception))
      result <- gameOpt match {
        case Some(game) => game.status match {
          case WaitingForUsers => {
            val newGameState = game.removePlayer(user.userId)
            gameStore.saveGameRecord(newGameState).bimap[LeaveGameError, LeaveGameSuccess](
              err => LeaveGameError.StoreError(err.exception),
              _ => LeaveGameSuccess(newGameState.gameId)
            )
          }
          case Active(awaitingMoveFrom) => {
            val newGameState = game.removePlayer(user.userId)
            gameStore.saveGameRecord(newGameState).bimap[LeaveGameError, LeaveGameSuccess](
              err => LeaveGameError.StoreError(err.exception),
              _ => LeaveGameSuccess(newGameState.gameId)
            )
          }
          case Finished(reason) => {
            IO.fail[LeaveGameError](LeaveGameError.NoSuchGameError)
          }
        }
        case None => IO.fail[LeaveGameError](LeaveGameError.NoSuchGameError)
      }
    } yield {
      result
    }

    resultF
  }
}
