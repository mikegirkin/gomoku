package net.girkin.gomoku.game

import java.util.UUID

import cats.effect.Concurrent
import fs2.concurrent.Queue
import net.girkin.gomoku.AuthUser
import zio.{IO, RefM, Task}
import zio.interop.catz._

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
  final case class StoreError(error: GameStore.StoreError) extends LeaveGameError
}

trait GameConcierge {
  def joinRandomGame(userId: UUID): IO[JoinGameError, JoinGameSuccess]
  def leaveGame(user: AuthUser): IO[LeaveGameError, LeaveGameSuccess]
}

class GameConciergeImpl(
  private val gameStore: GameStore,
  private val gameStreams: RefM[List[GameStream]]
) extends GameConcierge {

  def joinRandomGame(userId: UUID): IO[JoinGameError, JoinGameSuccess] = {
    val gameF = for {
      streams <- gameStreams
      games <- streams.getGamesAwaitingPlayers()
      gameToAddPlayer = games.headOption.getOrElse(
        Game.create(Ruleset.height, Ruleset.width, Ruleset.winningCondition)
      )
      updatedGame <- {
        if (gameToAddPlayer.players.contains(userId)) IO.succeed(gameToAddPlayer)
        else {
          val updatedGame = gameToAddPlayer.addPlayer(userId)
          gameStore.saveGameRecord(updatedGame).map(_ => updatedGame)
        }
      }
    } yield {
      JoinGameSuccess(updatedGame.gameId)
    }

    gameF.mapError(
      error => JoinGameError(error.toString)
    )
  }

  override def leaveGame(user: AuthUser): IO[LeaveGameError, LeaveGameSuccess] = {
    val resultF: IO[LeaveGameError, LeaveGameSuccess] = for {
      gameOpt <- gameStore.getActiveGameForPlayer(user).mapError(storeError => LeaveGameError.StoreError(storeError))
      result <- gameOpt match {
        case Some(game) => game.status match {
          case WaitingForUsers => {
            val newGameState = game.removePlayer(user.userId)
            gameStore.saveGameRecord(newGameState).bimap[LeaveGameError, LeaveGameSuccess](
              err => LeaveGameError.StoreError(err),
              _ => LeaveGameSuccess(newGameState.gameId)
            )
          }
          case Active(awaitingMoveFrom) => {
            val newGameState = game.removePlayer(user.userId)
            gameStore.saveGameRecord(newGameState).bimap[LeaveGameError, LeaveGameSuccess](
              err => LeaveGameError.StoreError(err),
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

  def getGameStream(gameId: UUID): IO[Nothing, Option[GameStream]] = {
    for {
      streams <- gameStreams.get
    } yield {
      streams.find(_.gameId == gameId)
    }
  }
}