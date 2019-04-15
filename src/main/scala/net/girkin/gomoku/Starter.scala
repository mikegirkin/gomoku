package net.girkin.gomoku

import cats.data.{Kleisli, OptionT}
import cats.effect.concurrent.Ref
import cats.effect.{Concurrent, ConcurrentEffect, ContextShift, Effect, ExitCode, IO, IOApp, Resource, Timer}
import cats.implicits._
import net.girkin.gomoku.auth.{AuthPrimitives, GoogleAuthImpl, SecurityConfiguration}
import net.girkin.gomoku.game.{Game, GameServerImpl, InmemGameStore}
import net.girkin.gomoku.users.{PsqlAnormUserStore, UserStore}
import org.http4s.{Request, Response}
import org.http4s.client.Client
import org.http4s.client.blaze.{BlazeClientBuilder, Http1Client}
import org.http4s.server.blaze.BlazeBuilder

import scala.concurrent.ExecutionContext

object Starter extends IOApp {


  override def run(args: List[String]): IO[ExitCode] = {
    fs2.Stream.force(
      ServerStream.stream[IO](Services.userStore)
    ).compile.drain.as(ExitCode.Success)
  }
}

object Services {

  val db = new PsqlPooledDatabase()
  val userStore = new PsqlAnormUserStore(db)
  val securityConfiguration = SecurityConfiguration(
    "270746747187-0ri8ig249up93ranj0l9qvpkhufaocv7.apps.googleusercontent.com",
    "WluSEQw9iNB2iIabeUDOf-no"
  )

  def gameService[F[_]: ConcurrentEffect](
    ref: Ref[F, List[Game]]
  ):Kleisli[OptionT[F, ?], Request[F], Response[F]] = {
    val gameStore = new InmemGameStore[F](ref)
    new GameService[F](
      authService[F](),
      new GameServerImpl[F](
        gameStore
      ),
      gameStore
    ).service
  }

  def authService[F[_]: Effect]() = {
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

object ServerStream {

  def stream[F[_]: ConcurrentEffect : ContextShift : Timer](
    userStore: UserStore[F]
  ): F[fs2.Stream[F, ExitCode]] =
    Ref[F].of(List.empty[Game]).map { ref =>
      val client= BlazeClientBuilder(ExecutionContext.global).resource
      val gameService = Services.gameService(ref)
      val authService = Services.authService[F]()
      val googleAuthService = Services.googleAuthService(userStore, client)
      val staticService = new StaticService[F]()
      BlazeBuilder[F]
        .bindHttp(Constants.port, "0.0.0.0")
        .mountService(gameService, "/")
        .mountService(googleAuthService.service, "/auth/google")
        .mountService(authService.service, "/auth")
        .mountService(staticService.service, "/static")
        .serve
    }
}

