package net.girkin.gomoku.game

import cats.data.Kleisli
import net.girkin.gomoku.AuthUser
import zio.interop.catz._
import zio.{IO, RefM, UIO, ZIO}

import java.util.UUID


sealed trait JoinGameSuccess
object JoinGameSuccess {
  final case object JoinedQueue extends JoinGameSuccess
  final case class JoinedGame(
    gameId: UUID,
    player1: UUID,
    player2: UUID
  ) extends JoinGameSuccess

  def joinedQueue: JoinGameSuccess = JoinedQueue
  def joinedGame(gameId: UUID, player1: UUID, player2: UUID): JoinGameSuccess = JoinedGame(gameId, player1, player2)
}


sealed trait JoinGameError
final case class UnexpectedJoinGameError (
  reason: String
) extends JoinGameError with CriticalFailure

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
  def joinRandomGame(userId: UUID): IO[JoinGameError, JoinGameSuccess]
  def leaveGame(user: AuthUser): IO[LeaveGameError, LeaveGameSuccess]
}

class GameConciergeImpl private (
  private val gameStore: GameStore,
  private val gameStreams: RefM[List[GameStream]],
  private val playerQueue: RefM[List[UUID]]
) extends GameConcierge {

  final val defaultRules = GameRules(3, 3, 3)

  override def joinRandomGame(userId: UUID): IO[JoinGameError, JoinGameSuccess] = {

    /***
     * @return Optional player to pair with and a new state of the waiting player list
     */
    def pairPlayerWithWaiting(userId: UUID, waitingPlayers: List[UUID]): (Option[UUID], List[UUID]) = {
      waitingPlayers.headOption.fold {
        (Option.empty[UUID], waitingPlayers.prepended(userId))
      } { waitingPlayerId =>
        (Option(waitingPlayerId), waitingPlayers.tail)
      }
    }

    def createNewGameForPlayers: Kleisli[UIO, (UUID, UUID), GameStream] = Kleisli { case (player1, player2) =>
      val game = Game.create(defaultRules, player1, player2)
      GameStream.make(gameStore, game)
    }

    def rememberGameStream: Kleisli[UIO, GameStream, GameStream] = Kleisli { stream =>
      gameStreams.modify { gameStreamsList =>
        ZIO.succeed(
          (stream, gameStreamsList.prepended(stream))
        )
      }
    }

    for {
      //find another player
      playerIdToPairWith <- playerQueue.modify { waitingPlayerList =>
        ZIO.succeed {
          pairPlayerWithWaiting(userId, waitingPlayerList)
        }
      }
      result <- playerIdToPairWith.fold(
        ZIO.succeed(JoinGameSuccess.joinedQueue)
      ) { pairPlayerId =>
        (createNewGameForPlayers andThen rememberGameStream).map {
          gameStream => JoinGameSuccess.joinedGame(gameStream.gameId, pairPlayerId, userId)
        }.run((userId, pairPlayerId))
      }
    } yield {
      result
    }
  }

  override def leaveGame(user: AuthUser): IO[LeaveGameError, LeaveGameSuccess] = {
    val resultF: IO[LeaveGameError, LeaveGameSuccess] = for {
      gameOpt <- gameStore.getActiveGameForPlayer(user).mapError(storeError => LeaveGameError.StoreError(storeError))
      result <- gameOpt match {
        case Some(game) => game.status match {
          case Active(awaitingMoveFrom) =>
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
          case Finished(reason) =>
            IO.fail[LeaveGameError](LeaveGameError.NoSuchGameError)
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

object GameConciergeImpl {
  def apply(gameStore: GameStore, gameStreams: List[GameStream], playerQueue: List[UUID]): UIO[GameConcierge] = {
    for {
      gameStreamsRef <- RefM.make(gameStreams)
      playerQueueRef <- RefM.make(playerQueue)
    } yield {
      new GameConciergeImpl(gameStore, gameStreamsRef, playerQueueRef)
    }
  }
}