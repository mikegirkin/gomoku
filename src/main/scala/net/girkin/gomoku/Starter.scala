package net.girkin.gomoku

import cats.effect._
import net.girkin.gomoku.api._
import net.girkin.gomoku.auth.{AuthPrimitives, GoogleAuthImpl, SecurityConfiguration}
import net.girkin.gomoku.game.{GameConciergeImpl, GameStore, GameStream, PsqlGameStore}
import net.girkin.gomoku.users.{PsqlUserStore, UserStore}
import org.http4s.HttpRoutes
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import org.http4s.syntax.all._
import zio.interop.catz._
import zio.interop.catz.implicits._
import zio.{Task, ZIO, _}

import java.util.UUID
import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

object Starter extends App with Http4sDsl[Task] {

  private def buildForRuntime[A](implicit runtime: Runtime[A]): ZIO[A, Throwable, Unit]  = {
    val client = BlazeClientBuilder[Task](ExecutionContext.global).resource
    val staticExecutionContext = scala.concurrent.ExecutionContext.fromExecutor(Executors.newFixedThreadPool(4))
    val staticService = new StaticRoutesHandler[Task](staticExecutionContext)
    val db = new PsqlPooledDatabase()
    val userStore: UserStore[Task] = new PsqlUserStore(db)
    val authService = Services.authService
    val googleAuthService = Services.googleAuthService(userStore, client)
    val gameStore = new PsqlGameStore(db)

    val app: ZIO[A, Throwable, Unit] = for {
      outboundChannels <- OutboundChannels.make()
      gameService <- Services.gameService(authService, outboundChannels, gameStore)
      debugService = new DebugRoutes(authService, outboundChannels, gameStore)

      httpRoutes = Router[Task](
        "/auth/google" -> googleAuthService.service,
        "/auth" -> authService.service,
        "/static" -> staticService.service,
        "/debug" -> debugService.routes,
        "/healthcheck" -> HttpRoutes.of[Task] {
          case GET -> Root => Ok("HEALTHCHECK")
        },
        "/" -> gameService
      ).orNotFound
      server <- BlazeServerBuilder[Task]
        .bindHttp(Constants.port, "0.0.0.0")
        .withHttpApp(Logger.httpApp(true, false)(httpRoutes))
        .serve
        .compile[Task, Task, cats.effect.ExitCode]
        .drain
    } yield {
      server
    }

    app
  }

  override def run(args: List[String]) = {

    val environment = ZIO.runtime[Unit]
    val server = environment.flatMap { env =>
      buildForRuntime(env)
    }

    val program = server.provide(())

    program.foldM(
      err => {
        println("Error")
        ZIO.succeed(zio.ExitCode.failure)
      },
      _ => {
        println("Success finish")
        ZIO.succeed(zio.ExitCode.success)
      }
    )
  }
}

object Services {

  val securityConfiguration = SecurityConfiguration(
    "270746747187-0ri8ig249up93ranj0l9qvpkhufaocv7.apps.googleusercontent.com",
    "WluSEQw9iNB2iIabeUDOf-no"
  )
  val authPrimitives = new AuthPrimitives[Task]
  val gameStreamsF = RefM.make[List[GameStream]](List.empty[GameStream])

  def gameService(
    authService: Auth[Task],
    outboundChannels: OutboundChannels,
    gameStore: GameStore
  ): Task[HttpRoutes[Task]] /*: Task[Kleisli[OptionT[Task, ?], Request[Task], Response[Task]]] */= {
    val gameStreams = List.empty
    val playerQueue = List.empty[UUID]
    for {
      concierge <- GameConciergeImpl(gameStore, gameStreams, playerQueue)
      gameService = new GameRoutesHandler(
        concierge,
        gameStore,
        outboundChannels
      )
    } yield {
      new GameRoutes(authService, gameService).service
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