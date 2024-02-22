package net.girkin.gomoku.game

import cats.data.Kleisli
import net.girkin.gomoku.api.JoinGameError.AlreadyInGameOrQueue
import net.girkin.gomoku.api.LeaveGameError.{NoSuchGameError, UserHasNoActiveGame}
import net.girkin.gomoku.game.GomokuGameError.BadRequest
import net.girkin.gomoku.game.GomokuGameResponse.StateChanged
import net.girkin.gomoku.{AuthUser, CriticalException}
import net.girkin.gomoku.api.{GameStateChanged, JoinGameError, JoinGameSuccess, LeaveGameError, MoveAttemptError}
import zio.interop.catz._
import zio.{IO, RefM, UIO, ZIO}
import net.girkin.gomoku.game.GomokuGameRequest.MakeMove

import java.util.UUID

trait GameConcierge {
  def joinRandomGame(userId: UUID): IO[JoinGameError, JoinGameSuccess]
  def leaveGame(user: AuthUser): IO[LeaveGameError, GameStateChanged]
  def attemptMove(user: AuthUser, row: Int, col: Int): IO[MoveAttemptError, GameStateChanged]
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
      //return error if already in the queue or there is a game with the requester
      currentQueue <- playerQueue.get
      _ <- ZIO.fromEither(Either.cond(!currentQueue.contains(userId), (), JoinGameError.alreadyInGameOrQueue))
      currentGames <- gameStore
        .getActiveGameForPlayer(AuthUser(userId))
        .mapError(err => JoinGameError.unexpectedJoinGameError(err.toString))
      _ <- ZIO.fromEither(Either.cond(currentGames.isEmpty, (), AlreadyInGameOrQueue))
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

  override def leaveGame(user: AuthUser): IO[LeaveGameError, GameStateChanged] = {
    for {
      game <- gameStore
        .getActiveGameForPlayer(user)
        .orDieWith(CriticalException.DatabaseError)
        .flatMap {
          case Some(value) => ZIO.succeed(value)
          case None => ZIO.fail(UserHasNoActiveGame)
        }
      gameStream <- getGameStream(game.gameId)
        .flatMap {
          case Some(stream) => ZIO.succeed(stream)
          case None => ZIO.fail(NoSuchGameError)
        }
      result <- gameStream.handleConcede(GomokuGameRequest.Concede(user.userId, game.gameId))
        .mapBoth(
          { case BadRequest(gameId, reason) => LeaveGameError.BadRequest(user.userId, gameId, reason) },
          { case StateChanged(gameId) => GameStateChanged(gameId, game.player1, game.player2) }
        )
    } yield {
      result
    }
  }

  override def attemptMove(user: AuthUser, row: Int, col: Int): IO[MoveAttemptError, GameStateChanged] = {
    for {
      game <- gameStore
        .getActiveGameForPlayer(user)
        .orDieWith(CriticalException.DatabaseError)
        .flatMap {
          case Some(value) => ZIO.succeed(value)
          case None => ZIO.fail(MoveAttemptError.UserHasNoActiveGame)
        }
      gameStream <- getGameStream(game.gameId)
        .flatMap {
          case Some(stream) => ZIO.succeed(stream)
          case None => ZIO.fail(MoveAttemptError.NoSuchGameError)
        }
      result <- gameStream.handleMakeMove(MakeMove(UUID.randomUUID(), user.userId, game.gameId, row, col))
        .mapBoth(
          { error => MoveAttemptError.impossibleMove },
          { case GomokuGameResponse.StateChanged(gameId) => GameStateChanged(gameId, game.player1, game.player2) }
        )
    } yield {
      result
    }
  }

  private def getGameStream(gameId: UUID): IO[Nothing, Option[GameStream]] = {
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