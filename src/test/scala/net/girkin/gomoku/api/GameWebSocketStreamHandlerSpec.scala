package net.girkin.gomoku.api

import io.circe.{Decoder, Encoder}
import io.circe.parser.decode
import io.circe.syntax._
import net.girkin.gomoku.AuthUser
import net.girkin.gomoku.api.ApiObjects._
import net.girkin.gomoku.game.{GameConciergeImpl, GameStore, JoinGameError, JoinGameSuccess}
import org.http4s.websocket.WebSocketFrame
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import zio.ZIO

import java.util.UUID

class GameWebSocketStreamHandlerSpec extends AnyWordSpec with Matchers with MockFactory {

  implicit val ec = scala.concurrent.ExecutionContext.global
  implicit val rt = zio.Runtime.default

  private def toWebsocketTextFrame[A : Encoder](value: A): WebSocketFrame.Text = {
    WebSocketFrame.Text(value.asJson.toString())
  }

  private def deserializeResponseFrames[A : Decoder](outboundWebsocketMessage: OutboundWebsocketMessage*): Seq[A] = {
    outboundWebsocketMessage
      .map(_.frame).collect {
      case f @ WebSocketFrame.Text(_) => f.str
    }.map { str =>
      decode[A](str)
    }.collect {
      case Right(x) => x
    }
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

      "return JoinedGame to both users when received second join request" in {
        (gameStore.getActiveGameForPlayer _).expects(authUser1)
          .returns(ZIO.succeed(Option.empty))
        (gameStore.getActiveGameForPlayer _).expects(authUser2)
          .returns(ZIO.succeed(Option.empty))

        val frame = toWebsocketTextFrame(IncomingGameMessage.requestJoinGame)
        val resultF = for {
          gameWebSocketStreamHandler <- gameWebSocketStreamHandlerF
          result1 <- gameWebSocketStreamHandler.processFrame(authUser1)(frame)
          result2 <- gameWebSocketStreamHandler.processFrame(authUser2)(frame)
        } yield {
          (result1, result2)
        }

        val (result1, result2) = rt.unsafeRun(resultF)

        result1 should have size (1)
        val contents1 = deserializeResponseFrames[JoinGameSuccess](result1:_*)
        contents1.head shouldBe JoinGameSuccess.joinedQueue

        result2 should have size (2)
        result2.map(_.destinationUser) should contain theSameElementsAs List(authUser1, authUser2)
        val contents2 = deserializeResponseFrames[JoinGameSuccess](result2:_*)
          .collect {
            case JoinGameSuccess.JoinedGame(_, user1, user2) => (user1, user2)
          }

        contents2 should contain theSameElementsAs(List(
          (authUser1.userId, authUser2.userId),
          (authUser1.userId, authUser2.userId)
        ))
      }

      "return BadRequest if same user tries to join second time" in {
        (gameStore.getActiveGameForPlayer _).expects(authUser1)
          .returns(ZIO.succeed(Option.empty))

        val frame = toWebsocketTextFrame(IncomingGameMessage.requestJoinGame)
        val testF = for {
          gameWebSocketHandler <- gameWebSocketStreamHandlerF
          result1 <- gameWebSocketHandler.processFrame(authUser1)(frame)
          result2 <- gameWebSocketHandler.processFrame(authUser1)(frame)
        } yield {
          (result1, result2)
        }

        val (result1, result2) = rt.unsafeRun(testF)

        result1 should have size(1)
        val contents1 = deserializeResponseFrames[JoinGameSuccess](result1.head)
        contents1.head shouldBe JoinGameSuccess.joinedQueue

        result2 should have size(1)
        val contents2 = deserializeResponseFrames[JoinGameError](result2.head)
        contents2.head shouldBe JoinGameError.AlreadyInGameOrQueue
      }
  }
}
