package net.girkin.gomoku.game

import java.time.Instant
import java.util.UUID

import cats.implicits._

import scala.util.{Failure, Success, Try}

sealed trait PlayerNumber {
  def asInt: Int
  def other: PlayerNumber
}

object PlayerNumber {
  case object First extends PlayerNumber {
    override def asInt: Int = 0
    override def other: PlayerNumber = Second
  }
  case object Second extends PlayerNumber {
    override def asInt: Int = 1
    override def other: PlayerNumber = First
  }

  def fromInt(value: Int): Try[PlayerNumber] = {
    value match {
      case 0 => Success(PlayerNumber.First)
      case 1 => Success(PlayerNumber.Second)
      case _ => Failure(new IllegalArgumentException(s"PlayerNumber could only be created from 0 or 1. Provided: ${value}"))
    }
  }
}

sealed trait GameFinishReason extends Product with Serializable
final case class PlayerQuit(playerNumber: PlayerNumber) extends GameFinishReason
case object Draw extends GameFinishReason
final case class PlayerWon(playerNumber: PlayerNumber) extends GameFinishReason

sealed trait GameStatus
case class Active(awaitingMoveFrom: PlayerNumber) extends GameStatus
case class Finished(reason: GameFinishReason) extends GameStatus

trait MoveError
case class ImpossibleMove(reason: String) extends MoveError
case object GameFinished extends MoveError

case class GameRules(height: Int, width: Int, winCondition: Int)

case class MoveAttempt(
  row: Int,
  column: Int,
  userId: UUID
)

case class GameField(
  height: Int,
  width: Int,
  state: Vector[Vector[Option[PlayerNumber]]]
) {

  def get(row: Int, col: Int): Option[PlayerNumber] = state(row)(col)

  def update(row: Int, col: Int, newValue: Option[PlayerNumber]): GameField = {
    this.copy(state = state.updated(row, state(row).updated(col, newValue)))
  }
}

object GameField {
  def apply(
    height: Int,
    width: Int
  ): GameField = new GameField(height, width, Vector.fill(height)(Vector.fill(width)(None)))
}

final case class Game(
  gameId: UUID,
  player1: UUID,
  player2: UUID,
  status: GameStatus,
  winCondition: Int,
  field: GameField,
  createdAt: Instant
) {
  def players: List[UUID] = List(player1, player2)

  def isMoveWrong(move: MoveAttempt): Option[MoveError] = {
    if (
      move.column < 0 ||
      move.column >= field.height ||
      move.row < 0 ||
      move.row >= field.height
    ) {
      Some(ImpossibleMove("Out of bounds"))
    } else {
      this.status match {
        case Finished(_) => Some(GameFinished)
        case Active(userNumber) if players(userNumber.asInt) != move.userId =>
          Some(ImpossibleMove("Wrong user"))
        case _ => None
      }
    }
  }

  def getPlayerNumber(userId: UUID): Option[PlayerNumber] = {
    if(player1 == userId) Some(PlayerNumber.First)
    else if(player2 == userId) Some(PlayerNumber.Second)
    else Option.empty
  }

  def makeMove(move: MoveAttempt): Either[MoveError, Game] = {
    for {
      _ <- isMoveWrong(move).toLeft(())
      currentUserNumber <- getPlayerNumber(move.userId).toRight(ImpossibleMove(s"Player ${move.userId} couldn't make this move"))
      newFieldState = this.field.update(move.row, move.column, Some(currentUserNumber))
      gameWithUpdatedField = this.copy(field = newFieldState)
      newStatus: GameStatus = gameWithUpdatedField.winner() match {
        case Some(winner) => Finished(PlayerWon(winner))
        case None => if(gameWithUpdatedField.isDraw) {
          Finished(Draw)
        } else {
          Active(currentUserNumber.other)
        }
      }
    } yield  {
      gameWithUpdatedField.copy(
        status = newStatus
      )
    }
  }

  def playerConceded(playerNumber: PlayerNumber): Game = {
    this.copy(status = Finished(PlayerQuit(playerNumber)))
  }

  private def hasCompleteLineStartingAt(row: Int, col: Int, length: Int, delta: (Int, Int)): Boolean = {
    if(
      row + delta._1 * (length - 1) >= field.width ||
      col + delta._2 * (length - 1) >= field.height
    ) false
    else {
      Range(0, length).map { index =>
        (row + delta._1 * index, col + delta._2 * index)
      }.map {
        case (r, c) => field.get(r, c)
      }.reduce[Option[PlayerNumber]] {
        case (a, b) => {
          if(a.isEmpty || b.isEmpty || a != b) None
          else a
        }
      }.isDefined
    }
  }

  private def winner(): Option[PlayerNumber] = {
    val horizontal = (1, 0)
    val vertical = (0, 1)
    val diagonal = (1, 1)

    val winner = for {
      row <- Range(0, field.height - winCondition)
      col <- Range(0, field.width - winCondition)
      delta <- List(horizontal, vertical, diagonal)
      winner <- field.get(row, col) if hasCompleteLineStartingAt(row, col, winCondition, delta)
    } yield winner

    winner.headOption
  }

  private def isDraw: Boolean = {
    (for {
      r <- Range(0, field.height)
      c <- Range(0, field.width) if field.get(r, c).isEmpty
    } yield (r, c)).headOption.isEmpty &&
    winner().isEmpty
  }
}

object Game {
  def create(rules: GameRules, player1Id: UUID, player2Id: UUID): Game = new Game(
    UUID.randomUUID(),
    player1Id,
    player2Id,
    Active(PlayerNumber.First),
    rules.winCondition,
    GameField(rules.height, rules.width),
    Instant.now()
  )
}
