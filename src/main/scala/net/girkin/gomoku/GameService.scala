package net.girkin.gomoku

import java.io.File
import java.nio.file.Paths
import java.util.UUID

import cats.effect.Effect
import cats.implicits._
import io.circe.syntax._
import fs2._
import fs2.async.mutable.Queue
import net.girkin.gomoku.game.{GameServer, GameStore, JoinGameSuccess}
import org.http4s._
import org.http4s.circe._
import org.http4s.twirl._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebsocketBits.{Text, WebSocketFrame}

import scala.concurrent.ExecutionContext
import scala.util.Try
import net.girkin.gomoku.api.ApiObjects._

class ArbitratyPathVar[A](cast: String => A) {
  def unapply(str: String): Option[A] =
    if (!str.isEmpty)
      Try(cast(str)).toOption
    else
      None
}

object UUIDVar extends ArbitratyPathVar[UUID](s => UUID.fromString(s))

class GameService[F[_]: Effect] (
  authService: Auth[F],
  gameServer: GameServer[F],
  gameStore: GameStore[F],
  implicit val ec: ExecutionContext
) extends Http4sDsl[F] {

  val anonymous = authService.authenticated(
    AuthedService[AuthToken, F] {
      case GET -> Root / "wsecho" as token => wsEcho(token)
    }
  )

  val securedService = authService.secured(
    AuthedService[AuthUser, F] {
      case GET -> Root as token => gameApp(token)
      case GET -> Root / UUIDVar(gameId) as token => game(token, gameId)
      case POST -> Root / "join" as token => joinRandomGame(token)
    }
  )

  val service: HttpService[F] = anonymous <+> securedService

  def gameApp(userToken: AuthUser): F[Response[F]] = {
    Ok(
      views.html.dashboard()
    )
  }

  def game(userToken: AuthUser, gameId: UUID): F[Response[F]] = {
    gameStore.getGame(gameId).fold(
      NotFound()
    ) {
      game => Ok(game.asJson)
    }.flatten
  }

  def joinRandomGame(token: AuthUser): F[Response[F]] = {
    gameServer.joinRandomGame(token.userId).fold[F[Response[F]]](
      error => BadRequest(""),
      {
        case JoinGameSuccess(gameID, webSocketUrl) =>
          Ok(Map(
            "gameId" -> gameID.toString,
            "webSocketUrl" -> webSocketUrl
          ).asJson)
      }
    ).flatten
  }

  def wsEcho(token: AuthToken) = {
    val echoReply: Pipe[F, WebSocketFrame, WebSocketFrame] =
      _.collect {
        case Text(msg, _) => Text("You sent the server: " + msg)
        case _ => Text("Something new")
      }

    Queue
      .unbounded[F, WebSocketFrame]
      .flatMap { q =>
        val d = q.dequeue.through(echoReply)
        val e = q.enqueue
        WebSocketBuilder[F].build(d, e)
      }
  }
}

