package net.girkin.gomoku

import cats.effect.Effect
import io.circe.Json
import org.http4s.HttpService
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl


class HealthCheckService[F[_]: Effect] extends Http4sDsl[F] {

  val service: HttpService[F] = {
    HttpService[F] {
      case GET -> Root / "_internal_" / "healthcheck" =>
        Ok(Json.obj("status" -> Json.fromString("ok")))
    }
  }
}

