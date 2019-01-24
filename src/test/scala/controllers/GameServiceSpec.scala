package controllers

import java.time.LocalDateTime
import java.util.UUID

import cats.Id
import cats.effect.IO
import fs2.async.Ref
import net.girkin.gomoku.auth.AuthPrimitives
import net.girkin.gomoku.game._
import net.girkin.gomoku.users.User
import net.girkin.gomoku.{Auth, AuthUser, Constants, GameService}
import org.http4s._
import org.http4s.dsl.io._
import org.scalatest.{Inside, MustMatchers, WordSpec}

class GameServiceSpec extends WordSpec
  with MustMatchers
  with Inside {

  implicit val ec = scala.concurrent.ExecutionContext.global

  "GameController 'JoinRandomGame'" should {
    val ref = Ref[IO, List[Game]](List.empty)
    val store = new InmemGameStore(ref.unsafeRunSync())
    val authService = new Auth[IO]
    val service = new GameService[IO](
      authService,
      new GameServerImpl[IO](store),
      store,
      ec
    ).service
    val url = Uri.uri("/join")

    val user1 = User(UUID.randomUUID(), "test1@example.com", LocalDateTime.now)
    val authToken = AuthUser.fromUser(user1)
    val nonce = "a87fvoajlhvlbhv"
    val joinRequest = Request[IO](method = Method.POST, uri = url)
      .withHeaders(Headers(
        Header("X-Requested-With", "XMLHttpRequest")
      ))

    val authPrimitives = new AuthPrimitives[Id]()
    val authenticatedRequest = joinRequest.addCookie(Constants.authCookieName, authPrimitives.signToken(authToken))


    "return 403 when not authenticated" in {
      val result = service.orNotFound.run(joinRequest).unsafeRunSync()

      result.status mustBe Status.Forbidden
    }

    "be able to join game when authenticated" in {
      val result = service.orNotFound.run(authenticatedRequest).unsafeRunSync()

      result.status mustBe Ok
      val gamesInStore = store.getGamesAwaitingPlayers().unsafeRunSync()
      gamesInStore must have size 1
      inside(gamesInStore.head) {
        case Game(_, players, status, _, _) =>
          players must have size 1
          players.head mustBe user1.id
          status mustBe WaitingForUsers
      }
    }

    "be able to prevent player from signing up for the game if they are waiting for one" in {
      val result = service.orNotFound.run(authenticatedRequest).unsafeRunSync()

      result.status mustBe Ok
      val gamesInStore = store.getGamesAwaitingPlayers().unsafeRunSync()
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
    val ref = Ref[IO, List[Game]](List.empty)
    val store = new InmemGameStore(ref.unsafeRunSync())
    val authService = new Auth[IO]
    val service: HttpService[IO] = new GameService[IO](
      authService,
      new GameServerImpl[IO](store),
      store,
      ec
    ).anonymous
    val url = Uri.uri("/wsecho")
    val request = Request[IO](uri = url)
      .withHeaders(Headers(
        Header("X-Requested-With", "XMLHttpRequest")
      ))

    "respond with echo" in {
      pending
    }
  }
}
