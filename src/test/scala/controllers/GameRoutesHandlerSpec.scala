package controllers

import java.time.LocalDateTime
import java.util.UUID

import cats.Id
import cats.effect.IO
import fs2.concurrent.Queue
import net.girkin.gomoku.api.{GameRoutesHandler, Routes}
import net.girkin.gomoku.auth.AuthPrimitives
import net.girkin.gomoku.game._
import net.girkin.gomoku.users.User
import net.girkin.gomoku.{Auth, AuthUser, Constants}
import org.http4s._
import org.http4s.syntax.all._
import org.http4s.server.Router
import org.http4s.websocket.WebSocketFrame
import org.scalatest.{Inside, MustMatchers, WordSpec}
import zio.{DefaultRuntime, Ref, RefM, Task}
import zio.interop.catz._

class GameRoutesHandlerSpec extends WordSpec
  with MustMatchers
  with Inside {

  implicit val ec = scala.concurrent.ExecutionContext.global
  implicit val rt = new DefaultRuntime {}

  "GameController 'JoinRandomGame'" should {
    val ref = Ref.make[List[Game]](List.empty)
    val store = new InmemGameStore(rt.unsafeRun(ref))
    val channelStore = RefM.make[Map[AuthUser, Queue[Task, WebSocketFrame]]](Map.empty)
    val authService = new Auth[Task]
    val gameService = new Routes(
      authService,
      new GameRoutesHandler(
        new GameServerImpl(store),
        store,
        rt.unsafeRun(channelStore)
      ),
    ).service

    val service = Router[Task](
      "/" -> gameService
    ).orNotFound

    val url = uri"/join"

    val user1 = User(UUID.randomUUID(), "test1@example.com", LocalDateTime.now)
    val authToken = AuthUser.fromUser(user1)
    val nonce = "a87fvoajlhvlbhv"
    val joinRequest = Request[Task](method = Method.POST, uri = url)
      .withHeaders(Headers.of(
        Header("X-Requested-With", "XMLHttpRequest")
      ))

    val authPrimitives = new AuthPrimitives[Id]()
    val authenticatedRequest = joinRequest.addCookie(Constants.authCookieName, authPrimitives.signToken(authToken))


    "return 403 when not authenticated" in {
      val result = rt.unsafeRun(service.run(joinRequest))

      result.status mustBe Status.Forbidden
    }

    "be able to join game when authenticated" in {
      val result = rt.unsafeRun(service.run(authenticatedRequest))

      result.status mustBe Status.Ok
      val gamesInStore = rt.unsafeRun(store.getGamesAwaitingPlayers())
      gamesInStore must have size 1
      inside(gamesInStore.head) {
        case Game(_, players, status, _, _) =>
          players must have size 1
          players.head mustBe user1.id
          status mustBe WaitingForUsers
      }
    }

    "be able to prevent player from signing up for the game if they are waiting for one" in {
      val result = rt.unsafeRun(service.run(authenticatedRequest))

      result.status mustBe Status.Ok
      val gamesInStore = rt.unsafeRun(store.getGamesAwaitingPlayers())
      gamesInStore must have size 1
      inside(gamesInStore.head) {
        case Game(_, players, status, _, _) =>
          players must have size 1
          players.head mustBe user1.id
          status mustBe WaitingForUsers
      }
    }
  }

  "GameService 'wsEcho'" should {
    val ref = Ref.make[List[Game]](List.empty)
    val store = new InmemGameStore(rt.unsafeRun(ref))
    val authService = new Auth[Task]
    val channelStore = RefM.make[Map[AuthUser, Queue[Task, WebSocketFrame]]](Map.empty)
    val gameService = new Routes(
      authService,
      new GameRoutesHandler(
        new GameServerImpl(store),
        store,
        rt.unsafeRun(channelStore)
      )
    ).service

    val service = Router[Task](
      "/" -> gameService
    ).orNotFound

    val url = uri"/wsecho"
    val request = Request[Task](uri = url)
      .withHeaders(Headers.of(
        Header("X-Requested-With", "XMLHttpRequest")
      ))

    "respond with echo" in {
      pending
    }
  }
}
