package net.girkin.gomoku

import java.util.concurrent.Executors

import cats.data.{Kleisli, OptionT}
import cats.effect._
import net.girkin.gomoku.api.{GameRoutesHandler, OutboundChannels, Routes, StaticRoutesHandler}
import net.girkin.gomoku.auth.{AuthPrimitives, GoogleAuthImpl, SecurityConfiguration}
import net.girkin.gomoku.game.{Game, GameConciergeImpl, InmemGameStore}
import net.girkin.gomoku.users.{PsqlAnormUserStore, UserStore}
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import org.http4s.syntax.all._
import org.http4s.{HttpRoutes, Request, Response}
import zio.clock.Clock
import zio.interop.catz._
import zio.interop.catz.implicits._
import zio.{Task, ZIO, _}

import scala.concurrent.ExecutionContext

object Starter extends App with Http4sDsl[Task] {

  private def buildForRuntime[A](implicit runtime: Runtime[A]): ZIO[A, Throwable, Unit]  = {
    val client = BlazeClientBuilder[Task](ExecutionContext.global).resource
    val userStore = Services.userStore
    val authService = Services.authService
    val googleAuthService = Services.googleAuthService(userStore, client)
    val staticExecutionContext = scala.concurrent.ExecutionContext.fromExecutor(Executors.newFixedThreadPool(4))
    val staticService = new StaticRoutesHandler[Task](staticExecutionContext)

    val app: ZIO[A, Throwable, Unit] = for {
      gameService <- Services.gameService(authService)
      httpRoutes = Router[Task](
        "/" -> gameService,
        "/auth/google" -> googleAuthService.service,
        "/auth" -> authService.service,
        "/static" -> staticService.service,
        "/healthcheck" -> HttpRoutes.of[Task] {
          case GET -> Root => Ok("HEALTHCHECK")
        }
      ).orNotFound
      server <- BlazeServerBuilder[Task]
        .bindHttp(Constants.port, "0.0.0.0")
        .withHttpApp(Logger.httpApp(true, false)(httpRoutes))
        .serve
        .compile[Task, Task, ExitCode]
        .drain
    } yield {
      server
    }

    app
  }

  override def run(args: List[String]): ZIO[Clock, Nothing, Int] = {

    val environment = ZIO.runtime[Unit]
    val server = environment.flatMap { env =>
      buildForRuntime(env)
    }

    val program = server.provide(())

    program.foldM(
      err => {
        println("Error")
        ZIO.succeed(1)
      },
      _ => {
        println("Success finish")
        ZIO.succeed(0)
      }
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
  val authPrimitives = new AuthPrimitives[Task]

  def gameService(
    authService: Auth[Task]
  ) /*: Task[Kleisli[OptionT[Task, ?], Request[Task], Response[Task]]] */= {
    for {
      ref <- zio.Ref.make(List.empty[Game])
      userChannels <- OutboundChannels.make()
      gameStore = new InmemGameStore(ref)
      gameService = new GameRoutesHandler(
        new GameConciergeImpl(
          gameStore
        ),
        gameStore,
        userChannels
      )
    } yield {
      new Routes(authService, gameService).service
    }
  }

  def authService(implicit ev: Effect[Task]): Auth[Task] = {
    new Auth[Task](authPrimitives)
  }

  def googleAuthService(
    userStore: UserStore[Task],
    httpClient: Resource[Task, Client[Task]]
  )(implicit ev: Effect[Task]) =
    new GoogleAuthImpl[Task](
      authPrimitives,
      userStore,
      securityConfiguration,
      httpClient
    )
}