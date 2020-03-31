package net.girkin.gomoku.game

import java.sql.Connection
import java.time.LocalDateTime
import java.util.UUID

import anorm.Macro.ColumnNaming
import net.girkin.gomoku.game.GameStoreRecord.Status
import net.girkin.gomoku.{AuthUser, Database}
import zio.IO

case class GameStoreRecord(
  id: UUID,
  createdAt: LocalDateTime,
  user1: Option[UUID],
  user2: Option[UUID],
  winningCondition: Int,
  status: Status.Status
)

object GameStoreRecord {
  object Status extends Enumeration {
    type Status = Value
    val Awaiting, Finished, Active = Value
  }

  def fromGame(game: Game): GameStoreRecord = {

  }
}

case class StoreError(exception: Exception)

trait GameStore {
  def getGamesAwaitingPlayers(): IO[StoreError, List[GameStoreRecord]]
  def getActiveGameForPlayer(user: AuthUser): IO[StoreError, Option[GameStoreRecord]]
  def saveGameRecord(game: GameStoreRecord): IO[StoreError, Unit]
  def getGame(id: UUID): IO[StoreError, Option[GameStoreRecord]]
}

class PsqlGameStore(
  db:  Database
) extends GameStore {

  import anorm._

  private implicit val statusColumn: Column[GameStoreRecord.Status.Status] =
    Column.of[String].mapResult { result: String =>
      if (result == GameStoreRecord.Status.Active.toString) Right(GameStoreRecord.Status.Active)
      else if (result == GameStoreRecord.Status.Awaiting.toString) Right(GameStoreRecord.Status.Awaiting)
      else if (result == GameStoreRecord.Status.Finished.toString) Right(GameStoreRecord.Status.Finished)
      else Left(SqlRequestError(new Exception("Couldn't convert string to GameStoreRecord")))
  }

  val storeRecordParser = Macro.namedParser[GameStoreRecord](ColumnNaming.SnakeCase)

  def dbIO[T](block: Connection => T): IO[StoreError, T] = IO {
    db.withConnection { block }
  }.mapError {
    case exc: Exception => StoreError(exc)
  }

  def getGamesAwaitingPlayers(): IO[StoreError, List[GameStoreRecord]] = dbIO { implicit cn =>
    SQL"select * from games where status = ${Status.Awaiting.toString}"
      .as(storeRecordParser.*)
  }

  override def getActiveGameForPlayer(user: AuthUser): IO[StoreError, Option[GameStoreRecord]] = dbIO { implicit cn =>
    SQL"""
      select id, created_at, user_1, user_2, winning_condition, status
      from games
      where status in (${Status.Awaiting.toString}, ${Status.Active.toString})
    """.as(
      storeRecordParser.singleOpt
    )
  }

  override def saveGameRecord(game: GameStoreRecord): IO[StoreError, Unit] = dbIO { implicit cn =>
    SQL"""
      insert into games (id, created_at, user_1, user_2, winning_condition, status)
      values (${game.id}::uuid, ${game.createdAt}, ${game.user1}::uuid, ${game.user2}::uuid, ${game.winningCondition}, ${game.status.toString})
      on conflict(id) do update set
        user_1 = excluded.user_1,
        user_2 = excluded.user_2,
        status = excluded.status
    """.executeInsert(
      anorm.SqlParser.scalar[java.util.UUID].singleOpt
    )
  }.map(_ => ())

  override def getGame(id: UUID): IO[StoreError, Option[GameStoreRecord]] = dbIO { implicit cn =>
    SQL"""
      select id, created_at, user_1, user_2, winning_condition, status
      from games
      where id = ${id}
    """.as(
      storeRecordParser.singleOpt
    )
  }
}
