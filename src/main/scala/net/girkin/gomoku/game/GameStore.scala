package net.girkin.gomoku.game

import anorm.Macro.ColumnNaming
import io.circe.syntax._
import java.sql.Connection
import java.time.LocalDateTime
import java.util.UUID
import net.girkin.gomoku.{AuthUser, Database}
import zio.IO
import net.girkin.gomoku.api.ApiObjects._

case class GameStoreRecord(
  id: UUID,
  createdAt: LocalDateTime,
  user1: Option[UUID],
  user2: Option[UUID],
  winningCondition: Int,
  status: GameStatus
)

case class MoveRecord(
  id: UUID,
  createdAt: LocalDateTime,
  gameId: UUID,
  userId: UUID,
  move: MoveAttempt
)

case class StoreError(exception: Exception)

trait GameStore {
  def getGamesAwaitingPlayers(): IO[StoreError, List[Game]]
  def getActiveGameForPlayer(user: AuthUser): IO[StoreError, Option[Game]]
  def saveGameRecord(game: Game): IO[StoreError, Unit]
  def getGame(id: UUID): IO[StoreError, Option[Game]]
}

class PsqlGameStore(
  db:  Database
) extends GameStore {

  import anorm._

  private implicit val statusColumn: Column[GameStatus] = {
    ???
//    Column.of[String].mapResult { result: String =>
//      if (result == GameStoreRecord.Status.Active.toString) Right(GameStoreRecord.Status.Active)
//      else if (result == GameStoreRecord.Status.Awaiting.toString) Right(GameStoreRecord.Status.Awaiting)
//      else if (result == GameStoreRecord.Status.Finished.toString) Right(GameStoreRecord.Status.Finished)
//      else Left(SqlRequestError(new Exception("Couldn't convert string to GameStoreRecord")))
  }

  val storeRecordParser = Macro.namedParser[GameStoreRecord](ColumnNaming.SnakeCase)

  def dbIO[T](block: Connection => T): IO[StoreError, T] = {
    IO {
      db.withConnection {
        block
      }
    }.mapError {
      case exc: Exception => StoreError(exc)
    }
  }

  private def fetchMoves(gameIds: List[UUID]): IO[StoreError, List[MoveRecord]] = {
    ???
  }

  def getGamesAwaitingPlayers(): IO[StoreError, List[Game]] = {
    val gameList = { cn: Connection =>
      IO {
        SQL"select * from games where status -> 'type' = ${WaitingForUsers.getClass.getSimpleName}"
          .as(storeRecordParser.*)(cn)
      }
    }

    val moves = { cn: Connection =>
      IO {
        SQL"select * from moves where game_id = "
      }
    }

    ???

  }

  override def getActiveGameForPlayer(user: AuthUser): IO[StoreError, Option[Game]] = dbIO { implicit cn =>
    WaitingForUsers.getClass.getSimpleName
    SQL"""
      select id, created_at, user_1, user_2, winning_condition, status
      from games
      where status -> 'type' in (${WaitingForUsers.getClass.getSimpleName}, ${classOf[Active].getSimpleName})
    """.as(
      storeRecordParser.singleOpt
    )

    ???
  }

  override def saveGameRecord(game: Game): IO[StoreError, Unit] = dbIO { implicit cn =>
    val status = game.status.asJson.toString
    SQL"""
      insert into games (id, created_at, user_1, user_2, winning_condition, status)
      values (${game.gameId}::uuid, ${game.createdAt}, ${game.player1}::uuid, ${game.player2}::uuid, ${game.winningCondition}, ${status}::json)
      on conflict(id) do update set
        user_1 = excluded.user_1,
        user_2 = excluded.user_2,
        status = excluded.status
    """.executeInsert(
      anorm.SqlParser.scalar[java.util.UUID].singleOpt
    )
  }.map(_ => ())

  override def getGame(id: UUID): IO[StoreError, Option[Game]] = dbIO { implicit cn =>
    SQL"""
      select id, created_at, user_1, user_2, winning_condition, status
      from games
      where id = ${id}
    """.as(
      storeRecordParser.singleOpt
    )

    ???
  }
}
