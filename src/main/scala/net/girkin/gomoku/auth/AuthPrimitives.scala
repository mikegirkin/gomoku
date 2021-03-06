package net.girkin.gomoku.auth

import java.util.Base64
import java.util.concurrent.TimeUnit

import cats._
import cats.implicits._
import net.girkin.gomoku.users.User
import net.girkin.gomoku.{AuthUser, Constants}
import org.http4s.{Response, ResponseCookie}

import scala.concurrent.duration.Duration
import scala.util.Random

class AuthPrimitives[F[_]: Monad] {

  def signToken(token: AuthUser): F[String] = {
    val serializedToken = Base64.getEncoder.encodeToString(token.userId.toString().getBytes("utf-8"))
    val chars = ('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')
    implicitly[Applicative[F]].pure(
      Range(1, 16).map { _ => Random.nextInt(chars.size) }.mkString("")
    ).map {
      nonce => Constants.crypto.signToken(serializedToken, nonce)
    }
  }

  def setAuthCookie(response: F[Response[F]], user: User): F[Response[F]] = {
    val token = AuthUser.fromUser(user)
    for {
      resp <- response
      signedToken <- signToken(token)
    } yield {
      resp.addCookie(
        ResponseCookie(
          Constants.authCookieName,
          signedToken,
          path = Some("/"),
          maxAge = Some(Duration(1, TimeUnit.DAYS).toMillis)
        )
      )
    }
  }

  def removeAuthCookie(response: Response[F]): Response[F] = {
    response.removeCookie(
      ResponseCookie(
        Constants.authCookieName,
        "",
        path = Some("/"),
        maxAge = Some(Duration(1, TimeUnit.DAYS).toMillis)
      )
    )
  }

}
