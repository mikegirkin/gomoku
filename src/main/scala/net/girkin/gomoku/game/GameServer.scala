package net.girkin.gomoku.game

import java.util.UUID

import zio.IO

object Ruleset {
  val height = 5
  val width = 5
  val winningCondition = 4
}

case class JoinGameSuccess(
  gameId: UUID,
  webSocketUrl: String
)

case class JoinGameError(
  reason: String
)

trait GameServer {
  def joinRandomGame(userId: UUID): IO[JoinGameError, JoinGameSuccess]
}

class GameServerImpl(
  gameStore: GameStore
) extends GameServer {

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
      val webSocketAddress = s"/ws/${updatedGame.gameId}"
      JoinGameSuccess(updatedGame.gameId, webSocketAddress)
    }

    gameF.mapError(
      exc => JoinGameError(exc.getMessage)
    )
  }
}
