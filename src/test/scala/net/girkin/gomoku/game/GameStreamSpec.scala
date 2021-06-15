package net.girkin.gomoku.game

import cats.arrow.FunctionK
import cats.data.EitherT
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Task, UIO}
import fs2._
import zio.interop.catz._
import zio.interop.catz.implicits._
import cats.effect._
import cats.~>
import org.http4s.Response

import java.util.UUID

class GameStreamSpec extends AnyWordSpec with Matchers with MockFactory {

  //implicit val compiler: fs2.Stream.Compiler[IO[GomokuError, *], Task] = ???

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
      val inputStream: fs2.Stream[UIO, GomokuRequest] = fs2.Stream(requests:_*).covary[UIO]

      val UIOtoTask: ~>[UIO, Task] = new FunctionK[UIO, Task] {
        override def apply[A](fa: UIO[A]): Task[A] = fa//.flatMap(Task.succeed(_))
      }


      for {
        gameStream <- GameStream.make(gameStore, game)
      } yield {
        val result = inputStream
          .through(gameStream.pipe)
          .compile[Task, Task, Either[GomokuError, GomokuResponse]]
          .toVector

      }

    }
  }

}
