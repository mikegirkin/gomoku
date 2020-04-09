package net.girkin.gomoku.users

import java.time.Instant
import java.util.UUID

import anorm.Macro.ColumnNaming
import net.girkin.gomoku.Database
import zio.Task

case class User(
  id: UUID,
  email: String,
  createdAt: Instant
)

trait UserStore[F[_]] {
  def getByEmail(email: String): F[Option[User]]
  def upsert(user: User): F[Unit]
}

class PsqlUserStore (
  db: Database
) extends UserStore[Task] {

  import anorm._

  val userParser = Macro.namedParser[User](ColumnNaming.SnakeCase)

  override def getByEmail(
    email: String
  ): Task[Option[User]] = Task {
    db.withConnection { implicit cn =>
      SQL"select * from users where email=$email".as(userParser.singleOpt)
    }
  }

  override def upsert(user: User): Task[Unit] = Task {
    db.withConnection { implicit cn =>
      SQL"""
        insert into users (id, email, created_at)
        values (${user.id}::uuid, ${user.email}, ${user.createdAt})
        on conflict(id) do nothing
      """.executeInsert(anorm.SqlParser.scalar[java.util.UUID].singleOpt)
    }
  }
}
