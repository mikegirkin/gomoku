package net.girkin.gomoku

import cats.effect.Effect
import cats.implicits._
import io.circe.syntax._
import fs2._
import fs2.async.mutable.Queue
import net.girkin.gomoku.game.{GameServer, JoinGameSuccess}
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebsocketBits.{Text, WebSocketFrame}

import scala.concurrent.ExecutionContext

class GameService[F[_]: Effect] (
  authService: AuthService[F],
  gameServer: GameServer[F],
  implicit val ec: ExecutionContext
) extends Http4sDsl[F] {

  val anonymous = authService.authenticated(
    AuthedService[AuthToken, F] {
      case GET -> Root / "wsecho" as token => wsEcho(token)
    }
  )

  val securedService = authService.secured(
    AuthedService[AuthUser, F] {
      case GET -> Root as token => index(token)
      case POST -> Root / "join" as token => joinRandomGame(token)
    }
  )

  val service: HttpService[F] = anonymous orElse securedService

  def index(token: AuthUser): F[Response[F]] = Ok("ok")

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

