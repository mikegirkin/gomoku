package net.girkin.gomoku.api

import java.util.UUID

sealed trait JoinGameSuccess

object JoinGameSuccess {
  final case object JoinedQueue extends JoinGameSuccess
  final case class JoinedGame(gameId: UUID, player1: UUID, player2: UUID) extends JoinGameSuccess

  def joinedQueue: JoinGameSuccess = JoinedQueue
  def joinedGame(
      gameId: UUID,
      player1: UUID,
      player2: UUID
  ): JoinGameSuccess =
    JoinedGame(gameId, player1, player2)
}

sealed trait JoinGameError

object JoinGameError {
  final case object AlreadyInGameOrQueue extends JoinGameError
  final case class UnexpectedJoinGameError(reason: String) extends JoinGameError

  def alreadyInGameOrQueue: JoinGameError = AlreadyInGameOrQueue
  def unexpectedJoinGameError(reason: String): JoinGameError = UnexpectedJoinGameError(reason)
}

final case class GameStateChanged(gameId: UUID, user1: UUID, user2: UUID)

sealed trait LeaveGameError extends Product with Serializable

object LeaveGameError {
  case object NoSuchGameError extends LeaveGameError
  case object UserHasNoActiveGame extends LeaveGameError
  final case class UserNotInThisGame(gameId: UUID, playerId: UUID) extends LeaveGameError
  final case class BadRequest(userId: UUID, gameId: UUID, reason: String) extends LeaveGameError
}

sealed trait MoveAttemptError extends Product with Serializable

object MoveAttemptError {
  case object NoSuchGameError extends MoveAttemptError
  case object UserHasNoActiveGame extends MoveAttemptError
  case object ImpossibleMove extends MoveAttemptError
  def impossibleMove: MoveAttemptError = ImpossibleMove
}

