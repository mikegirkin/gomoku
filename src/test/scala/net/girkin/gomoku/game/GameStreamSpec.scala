package net.girkin.gomoku.game

import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Task, UIO}
import fs2._
import zio.interop.catz._
import zio.interop.catz.implicits._
import cats.effect._
import net.girkin.gomoku.game.GomokuError.BadMoveRequest
import net.girkin.gomoku.game.GomokuResponse.StateChanged

import java.util.UUID

class GameStreamSpec extends AnyWordSpec with Matchers with MockFactory {

  //implicit val compiler: fs2.Stream.Compiler[IO[GomokuError, *], Task] = ???
  implicit val ec = scala.concurrent.ExecutionContext.global
  implicit val rt = zio.Runtime.default

  "it" should {
    "work" in {
      val gameStore = mock[GameStore]
      val player1 = UUID.randomUUID()
      val player2 = UUID.randomUUID()
      val game = Game.create(GameRules(3, 3, 3), player1, player2)

      val requests = List(
        GomokuRequest.makeMove(UUID.randomUUID(), player1, game.gameId, 0, 0),
        GomokuRequest.makeMove(UUID.randomUUID(), player2, game.gameId, 0, 0)
      )
      val inputStream: fs2.Stream[UIO, GomokuRequest] = fs2.Stream(requests: _*).covary[UIO]

      (gameStore.saveMove _)
        .expects(game, MoveAttempt(0 ,0, player1))
        .returning(IO.succeed(MoveAttempt(0 ,0, player1)))

      val test = for {
        gameStream <- GameStream.make(gameStore, game)
        result <- inputStream
          .through(gameStream.pipe)
          .compile[Task, Task, Either[GomokuError, GomokuResponse]]
          .toVector
      } yield {
        val expected = List(
          Right(StateChanged(game.gameId)),
          Left(GomokuError.badMoveRequest(ImpossibleMove("Cell is taken"), game.gameId, player2))
        )

        result should contain theSameElementsAs expected
      }

      rt.unsafeRun(test)
    }
  }

}
