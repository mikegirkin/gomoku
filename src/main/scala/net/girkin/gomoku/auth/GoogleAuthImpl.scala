package net.girkin.gomoku.auth

import java.time.LocalDateTime
import java.util.{Base64, UUID}

import cats.data.EitherT
import cats.syntax.either._
import cats.effect.IO
import io.circe.Json
import javax.inject.Inject
import net.girkin.gomoku._
import org.http4s.client.blaze.Http1Client
import play.api.Configuration
import play.api.mvc._
import users.{User, UserStore}

class GoogleAuthImpl @Inject() (
  loggingAction: LoggingAction,
  userStore: UserStore,
  config: Configuration
) extends Logging {

  import PlaySecurity._

  def startGoogleAuth[A](redirectUri: String): Request[A] => Result =
    request => {
      val state = UUID.randomUUID().toString
      val nonce = UUID.randomUUID().toString

      Results.Redirect("https://accounts.google.com/o/oauth2/v2/net.girkin.gomoku.auth",
        Map[String, String](
          "client_id" -> config.get[String]("net.girkin.gomoku.auth.google.clientId"),
          "response_type" -> "code",
          "scope" -> "openid email",
          "redirect_uri" -> redirectUri,
          "state" -> state,
          "nonce" -> nonce
        ).mapValues(s => Seq(s))
      ).withCookies(Cookie(
        "google-net.girkin.gomoku.auth-state",
        state,
        maxAge = Some(5 * 60),
        path = "/"
      ))
    }

  def googleCallback[A]:Request[A] => IO[Result] = //loggingAction.io { implicit
    implicit request => {
      val code = Either.fromOption(
        for {
          req_state <- request.queryString.get("state").flatMap(_.headOption)
          cookie_state <- request.cookies.get("google-net.girkin.gomoku.auth-state").map(_.value)
          result <- request.queryString.get("code").flatMap(_.headOption) if cookie_state == req_state
        } yield result,
        GoogleIncomingRequestParsingError
      )

      val response: EitherT[IO, GomokuError, Result] = for {
        code <- EitherT.fromEither[IO](code)
        userData <- EitherT.right[GomokuError](requestGooogleUserData(code))
        email <- {
          EitherT.fromEither[IO](userData.hcursor.get[String]("email").leftMap[GomokuError](
            _ => GoogleAuthResponseIncomprehensible))
        }
        user <- EitherT.right[GomokuError](getOrCreateUser(email))
      } yield {
        logger.info(s"Logging in user ${user}")
        Results.Redirect(routes.HomeController.index()).login(user)
      }

      response.fold(
        {
          case GoogleIncomingRequestParsingError => Results.BadRequest
          case GoogleAuthResponseIncomprehensible => Results.InternalServerError
        },
        identity
      ).map { result =>
        result.discardingCookies(DiscardingCookie("google-net.girkin.gomoku.auth-state"))
      }
    }

  private def requestGooogleUserData(code: String) = {
    import io.circe.generic.extras.Configuration
    import io.circe.generic.extras.semiauto._
    import org.http4s.Method._
    import org.http4s._
    import org.http4s.circe.CirceEntityCodec._
    import org.http4s.client._
    import org.http4s.client.dsl.io._

    case class GoogleUserResponse(tokenType: String, idToken: String)

    implicit val snakeCaseConfig = Configuration.default.withSnakeCaseMemberNames
    implicit val decoder = deriveDecoder[GoogleUserResponse]

    val client: Client[IO] = Http1Client[IO]().unsafeRunSync()
    val requestBody = Seq(
      "code" -> code,
      "client_id" -> config.get[String]("net.girkin.gomoku.auth.google.clientId"),
      "client_secret" -> config.get[String]("net.girkin.gomoku.auth.google.clientSecret"),
      "redirect_uri" -> s"http://localhost:9000${routes.HomeController.googleCallback()}",
      "grant_type" -> "authorization_code"
    )

    val request = POST(
      Uri.uri("https://www.googleapis.com/oauth2/v4/token"),
      UrlForm(requestBody:_*)
    )

    for {
      googleUserResponse <- client.expect[GoogleUserResponse](request)
    } yield {
      /* TODO: This require extracting keys from google discovery document to do the correct validation
       ref http://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata
      */
      io.circe.parser.parse(
        new String(
          Base64.getDecoder.decode(
            googleUserResponse.idToken.split('.')(1)
          )
        )
      ).getOrElse(Json.Null)
    }
  }

  private def getOrCreateUser(email: String): IO[User] = {
    for {
      storedUser <- userStore.getByEmail(email)
      resultingUser <- {
        storedUser.fold {
          val newUser = User(UUID.randomUUID(), email, LocalDateTime.now)
          userStore.upsert(newUser).map{ _ => newUser }
        } {
          u => IO.pure(u)
        }
      }
    } yield resultingUser
  }
}


