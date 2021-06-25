package net.girkin.gomoku.game

import java.util.UUID
import cats.effect.Concurrent
import fs2.concurrent.Queue
import net.girkin.gomoku.AuthUser
import zio.{IO, RefM, Task, ZIO}
import zio.interop.catz._

final case class JoinedGame(
  gameId: UUID,
  players: (UUID, UUID)
)

sealed trait JoinGameError
final case class UnexpectedJoinGameError (
  reason: String
) extends JoinGameError with CriticalFailure
case object JoinedQueue extends JoinGameError


case class LeaveGameSuccess(
  gameId: UUID
)

sealed trait LeaveGameError extends Product with Serializable
object LeaveGameError {
  case object NoSuchGameError extends LeaveGameError
  final case class UserNotInThisGame(gameId: UUID, playerId: UUID) extends LeaveGameError
  final case class StoreError(error: GameStore.StoreError) extends LeaveGameError
}

trait GameConcierge {
  def joinRandomGame(userId: UUID): IO[JoinGameError, JoinedGame]
  def leaveGame(user: AuthUser): IO[LeaveGameError, LeaveGameSuccess]
}

class GameConciergeImpl(
  private val gameStore: GameStore,
  private val gameStreams: RefM[List[GameStream]],
  private val playerQueue: RefM[List[UUID]]
) extends GameConcierge {

  final val defaultRules = GameRules(3, 3, 3)

  override def joinRandomGame(userId: UUID): IO[JoinGameError, JoinedGame] = {

    /***
     * @param userId
     * @param waitingPlayers
     * @return Optional player to pair with and a new state of the waiting player list
     */
    def pairPlayerWithWaiting(userId: UUID, waitingPlayers: List[UUID]): (Option[UUID], List[UUID]) = {
      waitingPlayers.headOption.fold {
        (Option.empty[UUID], waitingPlayers.prepended(userId))
      } { waitingPlayerId =>
        (Option(waitingPlayerId), waitingPlayers.tail)
      }
    }


    for {
      //find another player
      playerIdToPairWith <- playerQueue.modify { waitingPlayerList =>
        ZIO.succeed {
          pairPlayerWithWaiting(userId, waitingPlayerList)
        }
      }.flatMap { playerIdOpt =>
        ZIO.fromOption(playerIdOpt).mapError(_ => JoinedQueue)
      }
      game = Game.create(defaultRules, playerIdToPairWith, userId)
      newGameStream <- GameStream.make(gameStore, game)
      _ <- gameStreams.update { gameStreamsList =>
        ZIO.succeed(gameStreamsList.prepended(newGameStream))
      }
    } yield {
      JoinedGame(game.gameId, (userId, playerIdToPairWith))
    }
  }

  override def leaveGame(user: AuthUser): IO[LeaveGameError, LeaveGameSuccess] = {
    val resultF: IO[LeaveGameError, LeaveGameSuccess] = for {
      gameOpt <- gameStore.getActiveGameForPlayer(user).mapError(storeError => LeaveGameError.StoreError(storeError))
      result <- gameOpt match {
        case Some(game) => game.status match {
          case Active(awaitingMoveFrom) => {
            val playerNumberOpt = game.getPlayerNumber(user.userId)
            playerNumberOpt.map {
              playerNumber =>
                val newGameState = game.playerConceded(playerNumber)
                gameStore.saveGameRecord(newGameState).bimap[LeaveGameError, LeaveGameSuccess](
                  err => LeaveGameError.StoreError(err),
                  _ => LeaveGameSuccess(newGameState.gameId)
                )
            }.getOrElse {
              IO.fail[LeaveGameError](LeaveGameError.UserNotInThisGame(game.gameId, user.userId))
            }
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