package net.girkin.gomoku.game

import java.time.LocalDateTime
import java.util.UUID

import anorm.Macro.ColumnNaming
import cats.Functor
import cats.effect.IO
import cats.implicits._
import fs2.async.Ref
import net.girkin.gomoku.Database

case class GameStoreRecord(
  id: UUID,
  createdAt: LocalDateTime,
  user1: Option[UUID],
  user2: Option[UUID],
  winningCondition: Int,
  status: String
)

trait GameStore[F[_]] {
  def getGamesAwaitingPlayers(): F[Seq[Game]]
  def saveGameRecord(game: Game): F[Unit]
}

class InmemGameStore[F[_]: Functor](activeGames: Ref[F, List[Game]]) extends GameStore[F] {
  override def getGamesAwaitingPlayers(): F[Seq[Game]] = {
    activeGames.get.map {
      _.filter { _.status == WaitingForUsers }
    }
  }

  override def saveGameRecord(game: Game): F[Unit] = {
    activeGames.modify { gameList =>
      if(gameList.exists(_.gameId == game.gameId)) {
        gameList.map { item =>
          if (item.gameId == game.gameId) game
          else item
        }
      } else {
        game :: gameList
      }
    }.void
  }
}

class PsqlGameStore(
  db:  Database
) {

  import anorm._

  val storeRecordParser = Macro.namedParser[GameStoreRecord](ColumnNaming.SnakeCase)

  def getGamesAwaitingPlayers(): IO[Seq[GameStoreRecord]] = IO {
    db.withConnection { implicit cn =>
      SQL"select * from games where status='awaiting'"
        .as(storeRecordParser.*)
    }
  }

  def saveGameRecord(
    game: GameStoreRecord
  ): IO[Unit] = IO {
    db.withConnection { implicit cn =>
      SQL"""
        insert into games (id, created_at, user_1, user_2, winning_condition, status)
        values (${game.id}::uuid, ${game.createdAt}, ${game.user1}::uuid, ${game.user2}::uuid, ${game.winningCondition}, ${game.status})
        on conflict(id) do update set
          user_1 = excluded.user_1,
          user_2 = excluded.user_2,
          status = excluded.status
      """.executeInsert(
        anorm.SqlParser.scalar[java.util.UUID].singleOpt
      )
    }
  }
}
