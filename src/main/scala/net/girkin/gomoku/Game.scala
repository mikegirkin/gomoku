package net.girkin.gomoku

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

sealed trait GameStatus
case object WaitingForUsers extends GameStatus
case class Active(awaitingMoveFrom: PlayerNumber) extends GameStatus
case object Finished extends GameStatus

case class GameField(
  height: Int,
  width: Int,
  private val state: Vector[Vector[Option[PlayerNumber]]]
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
  users: List[UUID],
  status: GameStatus,
  winningCondition: Int,
  field: GameField
) {
  def addUser(user: UUID): Game = {
    if(users.isEmpty) {
      this.copy(users = user :: users)
    } else if(users.size == 1) {
      this.copy(users = List(users.head, user), status = Active(PlayerNumber.First))
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
        case Finished => Some(GameFinished)
        case Active(userNumber) if users(userNumber.asInt) != move.userId =>
          Some(ImpossibleMove("Wrong user"))
        case _ => None
      }
    }
  }

  def getPlayerNumber(userId: UUID): PlayerNumber = {
    if(users(0) == userId) PlayerNumber.First
    else PlayerNumber.Second
  }

  def makeMove(move: MoveAttempt): Either[MoveError, Game] = {
    isMoveWrong(move).toLeft {
      val currentUserNumber = getPlayerNumber(move.userId)
        this.copy(
        field = this.field.update(move.row, move.column, Some(currentUserNumber)),
        status = Active(currentUserNumber.other)
      )
    }
  }

  def hasCompleteLineStartingAt(row: Int, col: Int, length: Int, delta: (Int, Int)): Boolean = {
    if(
      row + delta._1 * (length - 1) >= field.width ||
      col + delta._2 * (length - 1) >= field.height
    ) false
    else {
      Range(0, length).map { index =>
        (delta._1 * index, delta._2 * index)
      }.map {
        case (r, c) => field.get(r, c)
      }.reduce[Option[PlayerNumber]] {
        case (a, b) if a.isEmpty || b.isEmpty || a != b => None
        case (a, b) if a == b => a
      }.isDefined
    }
  }

  def winner(): Option[PlayerNumber] = {
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

trait MoveError
case object GameNotStarted extends MoveError
case class ImpossibleMove(reason: String) extends MoveError
case object GameFinished extends MoveError

case class MoveAttempt(
  row: Int,
  column: Int,
  userId: UUID
)

class GomokuGame(
  gameService: GameService
) {
  def create(height: Int, width: Int, winningCondition: Int): IO[Game] = {
    val game = Game.create(height, width, winningCondition)
    gameService.save(game)
  }

  def addUser(game: Game, user: UUID): IO[Game] = {
    gameService.save(
      game.addUser(user)
    )
  }

  def makeMove(game: Game, moveAttempt: MoveAttempt): IO[Either[MoveError, Game]] = ???
}

trait GameService {
  def save(game: Game): IO[Game]
}
