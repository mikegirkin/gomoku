package net.girkin.gomoku.game

import java.sql.Connection
import java.time.Instant
import java.util.UUID

import anorm.Macro.ColumnNaming
import cats.implicits._
import anorm.postgresql._
import cats.data.OptionT
import io.circe.Json
import io.circe.parser.parse
import io.circe.syntax._
import net.girkin.gomoku.api.ApiObjects._
import net.girkin.gomoku.{AuthUser, Database}
import org.postgresql.util.PGobject
import zio.IO
import zio.interop.catz._

case class GameStoreRecord(
  id: UUID,
  createdAt: Instant,
  user1: UUID,
  user2: UUID,
  winningCondition: Int,
  fieldHeight: Int,
  fieldWidth: Int,
  status: GameStatus
) {
  def toGame() = {
    Game(id, user1, user2, status, winningCondition, GameField(fieldHeight, fieldWidth), createdAt)
  }
}

case class MoveRecord(
  id: UUID,
  createdAt: Instant,
  gameId: UUID,
  userId: UUID,
  row: Int,
  column: Int
) {
  def toMoveAttempt() = {
    MoveAttempt(row, column, userId)
  }
}

object GameStore {
  sealed trait StoreError extends Product with Serializable
  object StoreError {
    def storeException(exception: Exception): StoreError = StoreException(exception)
    def storeInconsistent(reason: String): StoreError = StoreInconsistent(reason)
  }

  final case class StoreException(exception: Exception) extends StoreError
  final case class StoreInconsistent(reason: String) extends StoreError
}

trait GameStore {
  import GameStore._

  def getActiveGameForPlayer(user: AuthUser): IO[StoreError, Option[Game]]
  def saveGameRecord(game: Game): IO[StoreError, Unit]
  def getGame(id: UUID): IO[StoreError, Option[Game]]
  def saveMove(game: Game, move: MoveAttempt): IO[StoreError, MoveAttempt]
}

class PsqlGameStore(
  db:  Database
) extends GameStore {

  import anorm._
  import GameStore._

  private implicit val jsonColumn: Column[Json] = {
    Column.nonNull[Json] { (value, meta) =>
      val str = value match {
        case o: PGobject => Some(o.getValue)
        case s: String => Some(s)
        case clob: java.sql.Clob => Some(
          clob.getSubString(1, clob.length.toInt))
        case _ => None
      }

      str match {
        case Some(js) => parse(js).leftMap(error =>
          SqlRequestError(new Exception(s"Couldn't convert string to GameStatus. Error: ${error}"))
        )
        case None => Left(TypeDoesNotMatch(s"Cannot convert $value:${value.getClass} to JsValue for column ${meta.column}"))
      }
    }
  }

  implicit val statusColumn = Column.of[Json].mapResult { value =>
    value.as[GameStatus].leftMap(failure => {
      SqlMappingError(s"Couldn't deserialize json into ${classOf[GameStatus].getSimpleName}")
    })
  }

  val gameStoreRecordParser = Macro.namedParser[GameStoreRecord](ColumnNaming.SnakeCase)
  val moveRecordParser = Macro.namedParser[MoveRecord](ColumnNaming.SnakeCase)

  def dbIO[T](block: Connection => T): IO[StoreError, T] = {
    IO {
      db.withConnection {
        block
      }
    }.mapError {
      case exc: Exception => StoreError.storeException(exc)
    }
  }

  private def fetchMoves(gameId: UUID): IO[StoreError, List[MoveRecord]] = dbIO { implicit cn =>
    SQL"""
      select id, created_at, game_id, user_id, row, "column" from moves
      where game_id = ${gameId}::uuid
    """.as(
      moveRecordParser.*
    )
  }

  private def fetchMovesForGame(game: Game): IO[StoreError, Game] = {
    for {
      moveRecords <- fetchMoves(game.gameId)
      moves = moveRecords.map(_.toMoveAttempt())
      gameWithMoves <- IO.fromEither {
        replayMoves(game, moves).leftMap[StoreError] { moveError =>
          StoreError.storeInconsistent(s"Error while loading moves to a game: ${moveError}")
        }
      }
    } yield {
      gameWithMoves
    }
  }

  override def saveMove(game: Game, move: MoveAttempt): IO[StoreError, MoveAttempt] = {
    dbIO { implicit cn =>
      val moveRecord = MoveRecord(UUID.randomUUID(), Instant.now(), game.gameId, move.userId, move.row, move.column)
      SQL"""
        insert into moves (id, created_at, game_id, user_id, row, "column")
        values (${moveRecord.id}::uuid, ${moveRecord.createdAt}, ${moveRecord.gameId}::uuid,
          ${moveRecord.userId}::uuid, ${moveRecord.row}, ${moveRecord.column})
      """.executeInsert(
        anorm.SqlParser.scalar[java.util.UUID].single
      )
    }.map {
      _ => move
    }
  }

  private def replayMoves(game: Game, moves: List[MoveAttempt]): Either[MoveError, Game] = {
    moves.foldRight(Either.right[MoveError, Game](game)){
      case (attempt, Right(game)) => game.makeMove(attempt)
      case (_, Left(err)) => Either.left[MoveError, Game](err)
    }
  }

  override def getActiveGameForPlayer(user: AuthUser): IO[StoreError, Option[Game]] = {
    val gameRecordF = dbIO { implicit cn =>
      SQL"""
        select id, created_at, user1, user2, winning_condition, field_height, field_width, status
        from games
        where status ->> 'type' = (${classOf[Active].getSimpleName})
          and (user1 = ${user.userId} or user2 = ${user.userId})
        order by created_at desc
        limit 1
      """.as(
        gameStoreRecordParser.singleOpt
      ).map(
        _.toGame()
      )
    }

    OptionT(gameRecordF).semiflatMap(fetchMovesForGame).value
  }

  override def saveGameRecord(game: Game): IO[StoreError, Unit] = dbIO { implicit cn =>
    val status = game.status.asJson.toString
    SQL"""
      insert into games (id, created_at, user1, user2, winning_condition, field_height, field_width, status)
      values (${game.gameId}::uuid, ${game.createdAt}, ${game.player1}::uuid, ${game.player2}::uuid, ${game.winCondition}, ${game.field.height}, ${game.field.width}, ${status}::json)
      on conflict(id) do update set
        user1 = excluded.user1,
        user2 = excluded.user2,
        status = excluded.status
    """.executeInsert(
      anorm.SqlParser.scalar[java.util.UUID].singleOpt
    )
  }.map(_ => ())

  override def getGame(id: UUID): IO[StoreError, Option[Game]] = dbIO { implicit cn =>
    val game = SQL"""
      select id, created_at, user1, user2, winning_condition, field_height, field_width, status
      from games
      where id = ${id}::uuid
    """.as(
      gameStoreRecordParser.singleOpt
    )

    game.map { _.toGame() }
  }
}
