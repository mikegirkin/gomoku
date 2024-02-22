package net.girkin.gomoku.game

import net.girkin.gomoku.game.GomokuGameResponse.StateChanged
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import zio.IO

import java.util.UUID

class GameStreamSpec extends AnyWordSpec with Matchers with MockFactory {

  implicit val ec = scala.concurrent.ExecutionContext.global
  implicit val rt = zio.Runtime.default

  "GameStream" should {
    "produce correct messages when given a stream of requests" in {
      val gameStore = mock[GameStore]
      val player1 = UUID.randomUUID()
      val player2 = UUID.randomUUID()
      val game = Game.create(GameRules(3, 3, 3), player1, player2)

      val requests = List(
        GomokuGameRequest.MakeMove(UUID.randomUUID(), player1, game.gameId, 0, 0),
        GomokuGameRequest.MakeMove(UUID.randomUUID(), player2, game.gameId, 0, 0)
      )

      (gameStore.saveMove _)
        .expects(game, MoveAttempt(0 ,0, player1))
        .returning(IO.succeed(MoveAttempt(0 ,0, player1)))

      val resultF = for {
        gameStream <- GameStream.make(gameStore, game)
        result <- IO.collectAll(requests.map(move => gameStream.handleMakeMove(move).either))
      } yield {
        result
      }

      val result = rt.unsafeRun(resultF)

      val expected = List(
        Right(StateChanged(game.gameId)),
        Left(GomokuGameError.badMoveRequest(ImpossibleMove("Cell is taken"), game.gameId, player2))
      )

      result should contain theSameElementsAs expected
    }
  }

}
