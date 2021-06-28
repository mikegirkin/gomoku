package net.girkin.gomoku.game

import net.girkin.gomoku.game.JoinGameSuccess.{JoinedGame, JoinedQueue}
import org.scalamock.scalatest.MockFactory
import org.scalatest.Inside
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID
import scala.concurrent.ExecutionContext

class GameConciergeSpec extends AnyWordSpec with Matchers with Inside with MockFactory {

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global
  implicit val rt: zio.Runtime[zio.ZEnv] = zio.Runtime.default

  "GameConcierge" should {
    val gameStore = mock[GameStore]
    val gameStreams = List.empty
    val playerQueue = List.empty
    val user1Id = UUID.randomUUID()
    val user2Id = UUID.randomUUID()

    "return JoinedQueue and JoinedGame correspondingly" when {
      "received 2 requests to join game" in {
        val test = for {
          concierge <- GameConciergeImpl(gameStore, gameStreams, playerQueue)
          result1 <- concierge.joinRandomGame(user1Id)
          result2 <- concierge.joinRandomGame(user2Id)
        } yield {
          (result1, result2)
        }

        val (result1, result2) = rt.unsafeRun(test)

        result1 shouldBe JoinedQueue

        inside(result2) {
          case JoinedGame(_, user1, user2) =>
            user1 shouldBe user1Id
            user2 shouldBe user2Id
        }
      }
    }
  }
}
