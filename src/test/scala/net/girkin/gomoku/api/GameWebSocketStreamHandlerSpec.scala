package net.girkin.gomoku.api

import io.circe.Encoder
import io.circe.parser.decode
import io.circe.syntax._
import net.girkin.gomoku.AuthUser
import net.girkin.gomoku.api.ApiObjects._
import net.girkin.gomoku.game.{GameConciergeImpl, GameStore, JoinGameSuccess}
import org.http4s.websocket.WebSocketFrame
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

class GameWebSocketStreamHandlerSpec extends AnyWordSpec with Matchers with MockFactory {

  implicit val ec = scala.concurrent.ExecutionContext.global
  implicit val rt = zio.Runtime.default

  private def toWebsocketTextFrame[A : Encoder](value: A) = {
    WebSocketFrame.Text(value.asJson.toString())
  }

  "Stream handler" should {
      val authUser1 = AuthUser(UUID.randomUUID())
      val authUser2 = AuthUser(UUID.randomUUID())

      val gameStore = mock[GameStore]
      val gameWebSocketStreamHandlerF = for {
        concierge <- GameConciergeImpl(gameStore, List.empty, List.empty)
        userChannel <- OutboundChannels.make()
      } yield {
        new GameWebSocketStreamHandler(concierge, userChannel)
      }

      val gameWebSocketStreamHandler = rt.unsafeRun(gameWebSocketStreamHandlerF)

      "return JoinQueue when received first join request" in {
        val joinGameRequest = IncomingGameMessage.requestJoinGame
        val frame = WebSocketFrame.Text(joinGameRequest.asJson.toString())
        val resultF = gameWebSocketStreamHandler.processFrame(authUser1)(frame)
        val result = rt.unsafeRun(resultF)

        val expectedFrame = toWebsocketTextFrame(JoinGameSuccess.joinedQueue)
        result should have size (1)
        result should contain(OutboundWebsocketMessage(authUser1, expectedFrame))
      }

      "return JoinedGame to both users when received second join request" in {
        val joinGameRequest = IncomingGameMessage.requestJoinGame
        val frame = WebSocketFrame.Text(joinGameRequest.asJson.toString())
        val resultF = gameWebSocketStreamHandler.processFrame(authUser2)(frame)
        val result = rt.unsafeRun(resultF)

        result should have size (2)
        result.map(_.destinationUser) should contain theSameElementsAs List(authUser1, authUser2)

        //Deserialize and extract users from the contents. GameId is irrelevant
        val contents = result
          .map(_.frame).collect {
          case f @ WebSocketFrame.Text(_) => f.str
        }.map { str =>
          decode[JoinGameSuccess](str)
        }.collect {
          case Right(JoinGameSuccess.JoinedGame(_, user1, user2)) => (user1, user2)
        }

        contents should contain theSameElementsAs(List(
          (authUser1.userId, authUser2.userId),
          (authUser1.userId, authUser2.userId)
        ))
      }
  }
}
