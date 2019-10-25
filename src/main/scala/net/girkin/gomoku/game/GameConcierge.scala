package net.girkin.gomoku.game

import java.util.UUID

import net.girkin.gomoku.AuthUser
import org.http4s.blaze.channel.nio1.NIO1SocketServerGroup
import zio.{IO, UIO}

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

case class NoSuchGameError()

trait GameConcierge {
  def joinRandomGame(userId: UUID): IO[JoinGameError, JoinGameSuccess]
  def leaveGame(user: AuthUser): UIO[LeaveGameSuccess]
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
      exc => JoinGameError(exc.getMessage)
    )
  }

  override def leaveGame(user: AuthUser): IO[NoSuchGameError, LeaveGameSuccess] = {


    for {
      gameOpt <- gameStore.getActiveGameForPlayer(user)
      result <- gameOpt match {
        case Some(game) => game.status match {
          case WaitingForUsers => {
            val newGameState = game.removePlayer(user.userId)
            gameStore.saveGameRecord(newGameState)
          }
          case Active(awaitingMoveFrom) => {

          }
          case Finished(reason) =>
        }
        case None => IO.fail(NoSuchGameError)
      }

    } yield {
      result
    }
  }
}
