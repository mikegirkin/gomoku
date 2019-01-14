package net.girkin.gomoku

import cats.effect.{Effect, IO}
import cats.implicits._
import fs2.StreamApp
import fs2.async.Ref
import net.girkin.gomoku.game.{Game, GameServerImpl, InmemGameStore}
import org.http4s.server.blaze.BlazeBuilder

import scala.concurrent.ExecutionContext

object Starter extends StreamApp[IO] {
  import scala.concurrent.ExecutionContext.Implicits.global

  def stream(args: List[String], requestShutdown: IO[Unit]) =
    fs2.Stream.force(
      ServerStream.stream[IO]
    )
}

object Services {

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
    new AuthService[F]()
  }
}

object ServerStream {

  def stream[F[_]: Effect]()(implicit ec: ExecutionContext): F[fs2.Stream[F, StreamApp.ExitCode]] =
    for {
      ref <- Ref[F, List[Game]](List.empty)
      gameService = Services.gameService(ref, ec)
      authService = Services.authService[F]()
    } yield {
      BlazeBuilder[F]
        .bindHttp(8080, "0.0.0.0")
        .mountService(gameService, "/")
        .mountService(authService.service, "/auth")
        .serve
    }
}

