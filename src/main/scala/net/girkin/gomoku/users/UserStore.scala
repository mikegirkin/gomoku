package net.girkin.gomoku.users

import java.time.LocalDateTime
import java.util.UUID

import anorm.Macro.ColumnNaming
import cats.effect.IO
import net.girkin.gomoku.Database

case class User(
  id: UUID,
  email: String,
  createdAt: LocalDateTime
)

trait UserStore[Eff[_]] {
  def getByEmail(email: String): Eff[Option[User]]
  def upsert(user: User): Eff[Unit]
}

class PsqlAnormUserStore (
  db: Database
) extends UserStore[IO] {

  import anorm._

  val userParser = Macro.namedParser[User](ColumnNaming.SnakeCase)

  override def getByEmail(
    email: String
  ): IO[Option[User]] = IO {
    db.withConnection { implicit cn =>
      SQL"select * from users where email=$email".as(userParser.singleOpt)
    }
  }

  override def upsert(user: User): IO[Unit] = IO {
    db.withConnection { implicit cn =>
      SQL"""
        insert into users (id, email, created_at)
        values (${user.id}::uuid, ${user.email}, ${user.createdAt})
        on conflict(id) do nothing
      """.executeInsert(anorm.SqlParser.scalar[java.util.UUID].singleOpt)
    }
  }
}
