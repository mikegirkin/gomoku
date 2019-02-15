package net.girkin.gomoku

import cats.effect.{Effect, IO}
import cats.implicits._
import fs2.StreamApp
import fs2.async.Ref
import net.girkin.gomoku.auth.{AuthPrimitives, GoogleAuthImpl, SecurityConfiguration}
import net.girkin.gomoku.game.{Game, GameServerImpl, InmemGameStore}
import net.girkin.gomoku.users.{PsqlAnormUserStore, UserStore}
import org.http4s.client.Client
import org.http4s.client.blaze.Http1Client
import org.http4s.server.blaze.BlazeBuilder

import scala.concurrent.ExecutionContext

object Starter extends StreamApp[IO] {
  import scala.concurrent.ExecutionContext.Implicits.global

  def stream(args: List[String], requestShutdown: IO[Unit]) =
    fs2.Stream.force(
      ServerStream.stream[IO](Services.userStore)
    )
}

object Services {

  val db = new PsqlPooledDatabase()
  val userStore = new PsqlAnormUserStore(db)
  val securityConfiguration = SecurityConfiguration(
    "270746747187-0ri8ig249up93ranj0l9qvpkhufaocv7.apps.googleusercontent.com",
    "WluSEQw9iNB2iIabeUDOf-no"
  )

  def gameService[F[_]: Effect](
    ref: Ref[F, List[Game]],
    executionContext: ExecutionContext
  ) =
    new GameService[F](
      authService[F](),
      new GameServerImpl[F](
        new InmemGameStore[F](ref)
      ),
      executionContext
    ).service

  def authService[F[_]: Effect]() = {
    new Auth[F]()
  }

  def googleAuthService[F[_]: Effect](
    userStore: UserStore[F],
    httpClient: Client[F]
  ) =
    new GoogleAuthImpl[F](
      new AuthPrimitives[F],
      userStore,
      securityConfiguration,
      httpClient
    )
}

object ServerStream {

  def stream[F[_]: Effect](
    userStore: UserStore[F]
  )(
    implicit ec: ExecutionContext
  ): F[fs2.Stream[F, StreamApp.ExitCode]] =
    Ref[F, List[Game]](List.empty).map { ref =>
      val result = for {
        client <- Http1Client.stream[F]()
        gameService = Services.gameService(ref, ec)
        authService = Services.authService[F]()
        googleAuthService = Services.googleAuthService(userStore, client)
      } yield {
        BlazeBuilder[F]
          .bindHttp(Constants.port, "0.0.0.0")
          .mountService(gameService, "/")
          .mountService(googleAuthService.service, "/auth/google")
          .mountService(authService.service, "/auth")
          .serve
      }

      result.flatten
    }
}

