package net.girkin.gomoku.game

import java.util.UUID

import cats.effect.IO

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
}

trait GameFinishReason
case class PlayerQuit(playerNumber: PlayerNumber) extends GameFinishReason
case object Draw extends GameFinishReason
case class PlayerWon(playerNumber: PlayerNumber) extends GameFinishReason

sealed trait GameStatus
case object WaitingForUsers extends GameStatus
case class Active(awaitingMoveFrom: PlayerNumber) extends GameStatus
case class Finished(reason: GameFinishReason) extends GameStatus

trait MoveError
case object GameNotStarted extends MoveError
case class ImpossibleMove(reason: String) extends MoveError
case object GameFinished extends MoveError

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

case class Game(
  gameId: UUID,
  players: List[UUID],
  status: GameStatus,
  winningCondition: Int,
  field: GameField
) {
  def addPlayer(user: UUID): Game = {
    if(players.isEmpty) {
      this.copy(players = user :: players)
    } else if(players.size == 1) {
      this.copy(players = List(players.head, user), status = Active(PlayerNumber.First))
    } else {
      this
    }
  }

  def removePlayer(userId: UUID): Game = {
    if (!this.status.isInstanceOf[Active]) {
      this
    } else if(players.contains(userId)) {
      this.copy(status = Finished(PlayerQuit(getPlayerNumber(userId))))
    } else {
      this
    }
  }

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
        case WaitingForUsers => Some(GameNotStarted)
        case Finished(_) => Some(GameFinished)
        case Active(userNumber) if players(userNumber.asInt) != move.userId =>
          Some(ImpossibleMove("Wrong user"))
        case _ => None
      }
    }
  }

  def getPlayerNumber(userId: UUID): PlayerNumber = {
    if(players(0) == userId) PlayerNumber.First
    else PlayerNumber.Second
  }

  def makeMove(move: MoveAttempt): Either[MoveError, Game] = {
    isMoveWrong(move).toLeft {
      val currentUserNumber = getPlayerNumber(move.userId)
      val newFieldState = this.field.update(move.row, move.column, Some(currentUserNumber))
      val gameWithUpdatedField = this.copy(field = newFieldState)
      val newStatus: GameStatus = gameWithUpdatedField.winner() match {
        case Some(winner) => Finished(PlayerWon(winner))
        case None => if(gameWithUpdatedField.isDraw()) {
          Finished(Draw)
        } else {
          Active(currentUserNumber.other)
        }
      }
      gameWithUpdatedField.copy(
        status = newStatus
      )
    }
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
        case (a, b) if a.isEmpty || b.isEmpty || a != b => None
        case (a, b) if a == b => a
      }.isDefined
    }
  }

  private def winner(): Option[PlayerNumber] = {
    val horizontal = (1, 0)
    val vertical = (0, 1)
    val diagonal = (1, 1)

    val winner = for {
      row <- Range(0, field.height - winningCondition)
      col <- Range(0, field.width - winningCondition)
      delta <- List(horizontal, vertical, diagonal)
      winner <- field.get(row, col) if hasCompleteLineStartingAt(row, col, winningCondition, delta)
    } yield winner

    winner.headOption
  }

  private def isDraw(): Boolean = {
    (for {
      r <- Range(0, field.height)
      c <- Range(0, field.width) if field.get(r, c).isEmpty
    } yield (r, c)).headOption.isEmpty &&
    winner().isEmpty
  }
}

object Game {
  def create(height: Int, width: Int, winningCondition: Int): Game = new Game(
    UUID.randomUUID(),
    List.empty,
    WaitingForUsers,
    winningCondition,
    GameField(height, width)
  )
}
