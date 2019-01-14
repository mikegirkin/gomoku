package net.girkin.gomoku.game

import java.util.UUID

import cats._, cats.implicits._
import javax.inject.Inject
import cats.data.EitherT

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

trait GameServer[F[_]] {
  def joinRandomGame(userId: UUID): EitherT[F, JoinGameError, JoinGameSuccess]
}

class GameServerImpl[F[_]: Monad] @Inject()(
  gameStore: GameStore[F]
) extends GameServer[F] {

  def joinRandomGame(userId: UUID): EitherT[F, JoinGameError, JoinGameSuccess] = {
    val gameF = for {
      games <- gameStore.getGamesAwaitingPlayers()
      gameToAddPlayer = games.headOption.getOrElse(
        Game.create(Ruleset.height, Ruleset.width, Ruleset.winningCondition)
      )
      updatedGame = {
        if(gameToAddPlayer.players.contains(userId)) gameToAddPlayer
        else gameToAddPlayer.addPlayer(userId)
      }
      _ <- gameStore.saveGameRecord(updatedGame)
    } yield {
      updatedGame
    }

    EitherT.right[JoinGameError](gameF.map {
      game =>
        val webSocketAddress = s"/ws/${game.gameId}"
        JoinGameSuccess(game.gameId, webSocketAddress)
    })
  }
}
