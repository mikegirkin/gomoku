package net.girkin.gomoku.controllers

import java.time.{Instant, LocalDateTime}
import java.util.UUID

import cats.Id
import net.girkin.gomoku.api.{GameRoutes, GameRoutesHandler, OutboundChannels}
import net.girkin.gomoku.auth.AuthPrimitives
import net.girkin.gomoku.game._
import net.girkin.gomoku.users.User
import net.girkin.gomoku.{Auth, AuthUser, Constants}
import org.http4s._
import org.http4s.server.Router
import org.http4s.syntax.all._
import org.scalamock.scalatest.MockFactory
import org.scalatest.Inside
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import zio.interop.catz._
import zio.{IO, Ref, RefM, Task}

class GameRoutesHandlerSpec extends AnyWordSpec
  with Matchers with Inside with MockFactory {

  implicit val ec = scala.concurrent.ExecutionContext.global
  implicit val rt = zio.Runtime.default

  trait Environment {
    val channelStore = OutboundChannels.make()
    val authService = new Auth[Task](new AuthPrimitives[Task])
    val mockStore = mock[GameStore]
    val gameStreams = List.empty
    val playerQueue = List.empty
    val concierge = rt.unsafeRun(GameConciergeImpl(mockStore, gameStreams, playerQueue))
    val gameService = new GameRoutes(
      authService,
      new GameRoutesHandler(
        concierge,
        mockStore,
        rt.unsafeRun(channelStore)
      ),
    ).service
    val service = Router[Task](
      "/" -> gameService
    ).orNotFound
  }

  "GameRoutesHandler 'JoinRandomGame'" should {
    val url = uri"/join"

    val user1 = User(UUID.randomUUID(), "test1@example.com", Instant.now)
    val authToken = AuthUser.fromUser(user1)
    val nonce = "a87fvoajlhvlbhv"
    val joinRequest = Request[Task](method = Method.POST, uri = url)
      .withHeaders(Headers.of(
        Header("X-Requested-With", "XMLHttpRequest")
      ))

    val authPrimitives = new AuthPrimitives[Id]()
    val authenticatedRequest = joinRequest.addCookie(Constants.authCookieName, authPrimitives.signToken(authToken))


    "return 403 when not authenticated" in new Environment {
      val result = rt.unsafeRun(service.run(joinRequest))

      result.status mustBe Status.Forbidden
    }
  }
}
