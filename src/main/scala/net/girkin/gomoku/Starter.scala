package net.girkin.gomoku

import cats.data.{Kleisli, OptionT}
import cats.effect._
import net.girkin.gomoku.api.{GameRoutesHandler, Routes, StaticRoutesHandler}
import net.girkin.gomoku.auth.{AuthPrimitives, GoogleAuthImpl, SecurityConfiguration}
import net.girkin.gomoku.game.{Game, GameServerImpl, InmemGameStore}
import net.girkin.gomoku.users.{PsqlAnormUserStore, UserStore}
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.syntax.all._
import org.http4s.websocket.WebSocketFrame
import org.http4s.{HttpApp, HttpRoutes, Request, Response}
import zio.clock.Clock
import zio.interop.catz._
import zio.interop.catz.implicits._
import zio.{Task, ZIO, _}

import scala.concurrent.ExecutionContext

object Starter extends App with Http4sDsl[Task] {

  private def buildForRuntime[A](implicit runtime: Runtime[A]): ZIO[A, Throwable, Unit]  = {
    val client = BlazeClientBuilder[Task](ExecutionContext.global).resource
    val userStore = Services.userStore
    val authService = Services.authService[Task]()
    val googleAuthService = Services.googleAuthService[Task](userStore, client)
    val staticService = new StaticRoutesHandler[Task]()

    val app: ZIO[A, Throwable, Unit] = for {
      gameService <- Services.gameService(authService)
    } yield {

      val httpRoutes = Router[Task](
        "/" -> gameService,
        "/auth/google" -> googleAuthService.service,
        "/auth" -> authService.service,
        "/static" -> staticService.service,
        "/healthcheck" -> HttpRoutes.of[Task] {
          case GET -> Root => Ok("HEALTHCHECK")
        }
      ).orNotFound

      BlazeServerBuilder[Task]
        .bindHttp(Constants.port, "0.0.0.0")
        .withHttpApp(HttpApp(httpRoutes.run))
        .serve
        .compile[Task, Task, ExitCode]
        .drain
    }

    app
  }

  override def run(args: List[String]): ZIO[Environment, Nothing, Int] = {

    type AppEnvironment = Clock

    val environment = ZIO.runtime[AppEnvironment]
    val server = environment.flatMap { env =>
      buildForRuntime(env)
    }

    val program = server.provideSome[Environment] { _ => new Clock.Live {} }

    program.foldM(
      err => ZIO.succeed(1),
      _ => ZIO.succeed(0)
    )
  }
}

object Services {

  val db = new PsqlPooledDatabase()
  val userStore: UserStore[Task] = new PsqlAnormUserStore(db)
  val securityConfiguration = SecurityConfiguration(
    "270746747187-0ri8ig249up93ranj0l9qvpkhufaocv7.apps.googleusercontent.com",
    "WluSEQw9iNB2iIabeUDOf-no"
  )

  def gameService(
    authService: Auth[Task]
  ): Task[Kleisli[OptionT[Task, ?], Request[Task], Response[Task]]] = {
    for {
      ref <- zio.Ref.make(List.empty[Game])
      userChannelsStore <- zio.RefM.make(Map.empty[AuthUser, fs2.concurrent.Queue[Task, WebSocketFrame]])
      gameStore = new InmemGameStore(ref)
      gameService = new GameRoutesHandler(
        new GameServerImpl(
          gameStore
        ),
        gameStore,
        userChannelsStore
      )
    } yield {
      new Routes(authService, gameService).service
    }
  }

  def authService[F[_]: Effect](): Auth[F] = {
    new Auth[F]()
  }

  def googleAuthService[F[_]: Effect](
    userStore: UserStore[F],
    httpClient: Resource[F, Client[F]]
  ) =
    new GoogleAuthImpl[F](
      new AuthPrimitives[F],
      userStore,
      securityConfiguration,
      httpClient
    )
}