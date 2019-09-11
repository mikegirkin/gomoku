package net.girkin.gomoku

import java.util.{Base64, UUID}

import cats.Applicative
import cats.data.{Kleisli, OptionT}
import cats.effect.Effect
import cats.implicits._
import io.circe.ObjectEncoder
import io.circe.generic.semiauto._
import io.circe.syntax._
import net.girkin.gomoku.auth.AuthPrimitives
import net.girkin.gomoku.users.User
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Location
import org.http4s.server.AuthMiddleware
import org.http4s.twirl._
import org.http4s.util.CaseInsensitiveString
import org.http4s.{AuthedService, HttpRoutes, Request, Response, Uri, headers}

sealed trait AuthToken
case class AuthUser(userId: UUID) extends AuthToken
case object Anonymous extends AuthToken

object AuthUser {
  def fromUser(user: User): AuthUser = new AuthUser(user.id)
}

sealed trait AuthErr
case object NoAuthCookie extends AuthErr
case object CookieInvalid extends AuthErr

class Auth[F[_]: Effect](
  authPrimitives: AuthPrimitives[F]
) extends Http4sDsl[F] with Logging {

  implicit val authErrEncoder: ObjectEncoder[AuthErr] = deriveEncoder[AuthErr]

  val service = HttpRoutes.of[F] {
    case GET -> Root / "login" => login()
    case GET -> Root / "logout" => logout()
  }

  def login(): F[Response[F]] = {
    Ok(views.html.login())
  }

  def logout(): F[Response[F]] = {
    SeeOther(
      Location(Uri.uri("/"))
    ).map { authPrimitives.removeAuthCookie }
  }


  def authenticate: Kleisli[F, Request[F], Either[AuthErr, AuthUser]] = Kleisli[F, Request[F], Either[AuthErr, AuthUser]]({ request =>
    implicitly[Applicative[F]].pure {
      for {
        header <- headers.Cookie.from(request.headers).toRight(NoAuthCookie.asInstanceOf[AuthErr])
        cookie <- header.values.find(_.name == Constants.authCookieName).toRight(NoAuthCookie)
        tokenStr <- Constants.crypto.validateSignedToken(cookie.content).toRight(CookieInvalid)
        token = AuthUser(UUID.fromString(
          new String(
            Base64.getDecoder().decode(tokenStr), "utf-8"
          )
        ))
      } yield {
        token
      }
    }
  })

  def allowAnonymous(authToken: Either[AuthErr, AuthUser]): Either[AuthErr, AuthToken] = authToken match {
    case Left(NoAuthCookie) => Right(Anonymous)
    case x => x
  }

  val onFailure: AuthedService[AuthErr, F] = Kleisli {
    req =>
      OptionT.liftF(
        if(req.req.headers
          .get(CaseInsensitiveString("X-Requested-With"))
          .exists { _.value == "XMLHttpRequest" }
        ) {
          Forbidden(req.authInfo.asJson).map(authPrimitives.removeAuthCookie)
        } else {
          SeeOther(
            Location(Uri.fromString(Constants.LoginUrl).toOption.get),
          ).map(authPrimitives.removeAuthCookie)
        }
      )
  }

  val secured = AuthMiddleware(authenticate, onFailure)

  val authenticated = AuthMiddleware(authenticate.map(allowAnonymous), onFailure)

}

