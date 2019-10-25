package net.girkin.gomoku.game

import java.time.LocalDateTime
import java.util.UUID

import anorm.Macro.ColumnNaming
import net.girkin.gomoku.{AuthUser, Database}
import zio.{IO, Ref}

case class GameStoreRecord(
  id: UUID,
  createdAt: LocalDateTime,
  user1: Option[UUID],
  user2: Option[UUID],
  winningCondition: Int,
  status: String
)

trait GameStore {
  def getGamesAwaitingPlayers(): IO[Throwable, List[Game]]
  def getActiveGameForPlayer(user: AuthUser): IO[Nothing, Option[Game]]
  def saveGameRecord(game: Game): IO[Throwable, Unit]
  def getGame(id: UUID): IO[Throwable, Option[Game]]
}

class InmemGameStore(activeGames: Ref[List[Game]]) extends GameStore {
  override def getGamesAwaitingPlayers(): IO[Throwable, List[Game]] = {
    activeGames.get.map {
      _.filter { _.status == WaitingForUsers }
    }
  }


  override def getActiveGameForPlayer(user: AuthUser): IO[Nothing, Option[Game]] = {
    activeGames.get.map {
      _.find { game => game.status.isInstanceOf[Active] && game.players.contains() }
    }
  }

  override def saveGameRecord(game: Game): IO[Throwable, Unit] = {
    activeGames.update { gameList =>
      if(gameList.exists(_.gameId == game.gameId)) {
        gameList.map { item =>
          if (item.gameId == game.gameId) game
          else item
        }
      } else {
        game :: gameList
      }
    }.unit
  }

  override def getGame(id: UUID): IO[Throwable, Option[Game]] = {
    activeGames.get.map { games =>
      games.find {
        _.gameId == id
      }
    }
}
}

class PsqlGameStore(
  db:  Database
) {

  import anorm._

  val storeRecordParser = Macro.namedParser[GameStoreRecord](ColumnNaming.SnakeCase)

  def getGamesAwaitingPlayers(): IO[Throwable, List[Game]] = IO {
    db.withConnection { implicit cn =>
      SQL"select * from games where status='awaiting'"
        .as(storeRecordParser.*)
      ???
    }
  }

  def saveGameRecord(
    game: GameStoreRecord
  ): IO[Throwable, Unit] = IO {
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
