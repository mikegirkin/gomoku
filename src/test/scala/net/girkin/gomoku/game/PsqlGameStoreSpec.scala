package net.girkin.gomoku.game

import java.time.Instant
import java.util.UUID

import net.girkin.gomoku.{AuthUser, PsqlPooledDatabase}
import net.girkin.gomoku.users.{PsqlUserStore, User}
import net.girkin.gomoku.util.tags.DbTest
import org.scalatest.Inside
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import zio.IO

@DbTest
class PsqlGameStoreSpec extends AnyWordSpec with Matchers with Inside {

  val db = new PsqlPooledDatabase()
  val store = new PsqlGameStore(db)
  val userStore = new PsqlUserStore(db)
  implicit val ec = scala.concurrent.ExecutionContext.global
  implicit val rt = zio.Runtime.default


  "PsqlGameStore" should {

    "be able to store moves for the game and fetch active game back" in {

      val user1 = User(UUID.randomUUID(), "1@test.com", Instant.now())
      rt.unsafeRunSync(userStore.upsert(user1))
      val user2 = User(UUID.randomUUID(), "2@test.com", Instant.now())
      rt.unsafeRunSync(userStore.upsert(user2))
      val game = Game.create(GameRules(5, 5, 4), user1.id, user2.id)

      rt.unsafeRunSync(store.saveGameRecord(game))

      val moves = Vector(
        MoveAttempt(1, 1, user1.id),
        MoveAttempt(2, 2, user2.id),
        MoveAttempt(3, 3, user1.id)
      )

      val movesPlayed = rt.unsafeRunSync {
        for {
          gameAfter0 <- IO.fromEither(game.makeMove(moves(0)))
          _ <- store.saveMove(gameAfter0, moves(0))
          gameAfter1 <- IO.fromEither(gameAfter0.makeMove(moves(1)))
          _ <- store.saveMove(gameAfter1, moves(1))
          gameAfter2 <- IO.fromEither(gameAfter1.makeMove(moves(2)))
          _ <- store.saveMove(gameAfter2, moves(2))
        } yield {
          gameAfter2
        }
      }

      movesPlayed.toEither should matchPattern {
        case Right(_) =>
      }

      val result = rt.unsafeRunSync(store.getActiveGameForPlayer(AuthUser(user1.id))).toEither

      inside(result) {
        case Right(Some(game)) =>
          game.field.get(1, 1) shouldBe Some(PlayerNumber.First)
          game.field.get(2, 2) shouldBe Some(PlayerNumber.Second)
          game.field.get(3, 3) shouldBe Some(PlayerNumber.First)
      }
    }
  }

}
