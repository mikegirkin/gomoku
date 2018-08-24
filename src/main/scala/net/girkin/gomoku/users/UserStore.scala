package net.girkin.gomoku.users

import java.time.LocalDateTime
import java.util.UUID

import anorm.Macro.ColumnNaming
import cats.effect.IO
import javax.inject.Inject
import play.api.db.Database

case class User(
  id: UUID,
  email: String,
  createdAt: LocalDateTime
)

trait UserStore {
  def getByEmail(email: String): IO[Option[User]]
  def upsert(user: User): IO[Unit]
}

class PsqlAnormUserStore @Inject() (
  db: Database
) extends UserStore {

  import anorm._

  val userParser = Macro.namedParser[User](ColumnNaming.SnakeCase)

  override def getByEmail(
    email: String
  ): IO[Option[User]] = IO {
    db.withConnection { implicit cn =>
      SQL"select * from net.girkin.gomoku.users where email=$email".as(userParser.singleOpt)
    }
  }

  override def upsert(user: User): IO[Unit] = IO {
    db.withConnection { implicit cn =>
      SQL"""
        insert into net.girkin.gomoku.users (id, email, created_at)
        values (${user.id}::uuid, ${user.email}, ${user.createdAt})
        on conflict(id) do nothing
      """.executeInsert(anorm.SqlParser.scalar[java.util.UUID].singleOpt)
    }
  }
}
