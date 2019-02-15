package net.girkin.gomoku.auth

import java.time.LocalDateTime
import java.util.{Base64, UUID}

import cats.data.EitherT
import cats.effect.Effect
import cats.implicits._
import cats.{Applicative, Monad}
import io.circe.{Decoder, Json}
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto._
import net.girkin.gomoku.Ops._
import net.girkin.gomoku._
import net.girkin.gomoku.users.{User, UserStore}
import org.http4s.client.Client
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Location
import org.http4s.{Uri, _}
import org.http4s._
import org.http4s.circe.CirceEntityCodec._
import scala.concurrent.duration._
import scala.util.Random

case class SecurityConfiguration(
  googleClientId: String,
  googleSecret: String
)

sealed trait GoogleAuthError
case class GoogleCallbackError(msg: String) extends GoogleAuthError
case object GoogleAuthResponseIncomprehensible extends GoogleAuthError

class GoogleAuthImpl[Eff[_]: Effect](
  authPrimitives: AuthPrimitives[Eff],
  userStore: UserStore[Eff],
  config: SecurityConfiguration,
  httpClient: Client[Eff]
) extends Http4sDsl[Eff] with Logging {

  val REDIRECT_AFTER_LOGIN_TO = s"http://${Constants.host}:${Constants.port}/auth/google/callback"

  val service = HttpService[Eff] {
    case GET -> Root / "begin" => startAuthProcess(REDIRECT_AFTER_LOGIN_TO)
    case request @ GET -> Root / "callback" => processCallback(request)
  }

  def startAuthProcess[A](redirectUri: String): Eff[Response[Eff]] = {
    val state = UUID.randomUUID().toString
    val nonce = UUID.randomUUID().toString

    SeeOther(
      Location(Uri.uri("https://accounts.google.com/o/oauth2/v2/auth").setQueryParams(
        Map[String, String](
          "client_id" -> config.googleClientId,
          "response_type" -> "code",
          "scope" -> "openid email",
          "redirect_uri" -> s"$REDIRECT_AFTER_LOGIN_TO",
          "state" -> state,
          "nonce" -> nonce
        ).mapValues(s => Seq(s))
      ))
    ).addCookie(Cookie(
      "google-auth-state",
      state,
      maxAge = Some(5 * 60),
      path = Some("/")
    ))
  }

  private def requestGooogleUserData(code: String)  = {
    case class GoogleUserResponse(tokenType: String, idToken: String)

    implicit val snakeCaseConfig: Configuration = Configuration.default.withSnakeCaseMemberNames
    implicit val decoder: Decoder[GoogleUserResponse] = deriveDecoder[GoogleUserResponse]

    val requestBody = Seq(
      "code" -> code,
      "client_id" -> config.googleClientId,
      "client_secret" -> config.googleSecret,
      "redirect_uri" -> REDIRECT_AFTER_LOGIN_TO,
      "grant_type" -> "authorization_code"
    )

    val request = Request[Eff](
        Method.POST,
        Uri.uri("https://www.googleapis.com/oauth2/v4/token"),
        headers = Headers(org.http4s.headers.`Content-Type`(MediaType.`application/x-www-form-urlencoded`))
      ).withBody(
        UrlForm(requestBody: _*)
      )(implicitly[Monad[Eff]], UrlForm.entityEncoder)

    for {
      req <- request
      googleUserResponse <- httpClient.expectOr[GoogleUserResponse](req) {
        error =>
          logger.info(error.toString())

          error.body.through(fs2.text.utf8Decode).compile.fold(Vector.empty[String])({
            case (acc, str) => acc :+ str
          }).map {
            v => logger.info(v.mkString("\n"))
          }.map {
            _ => new Exception("failed on requesting stuff from google")
          }
      }
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

  private def getOrCreateUser(email: String): Eff[User] = {
    for {
      storedUser <- userStore.getByEmail(email)
      resultingUser <- {
        storedUser.fold {
          val newUser = User(UUID.randomUUID(), email, LocalDateTime.now)
          userStore.upsert(newUser).map{ _ => newUser }
        } {
          u => implicitly[Monad[Eff]].pure(u)
        }
      }
    } yield resultingUser
  }

  def processCallback(request: Request[Eff]): Eff[Response[Eff]] = {
    val code = for {
      req_state <- request.params.get("state") ?> GoogleCallbackError("Parameter state not found")
      cookie <- headers.Cookie.from(request.headers) ?> GoogleCallbackError("Required cookie not found")
      cookie_state <- cookie.values.find(_.name == "google-auth-state") ?> GoogleCallbackError("Required cookie not found")
      result <- if (cookie_state.content == req_state) {
        request.params.get("code") ?> GoogleCallbackError("Required cookie not found")
      } else {
        Either.left(GoogleCallbackError("Required cookie not found"))
      }
    } yield result

    val response: EitherT[Eff, GoogleAuthError, Eff[Response[Eff]]] = for {
      code <- EitherT.fromEither[Eff](code)
      userData <- EitherT.right[GoogleAuthError](requestGooogleUserData(code))
      email <- {
        EitherT.fromEither[Eff](userData.hcursor.get[String]("email").leftMap[GoogleAuthError](
          _ => GoogleAuthResponseIncomprehensible))
      }
      user <- EitherT.right[GoogleAuthError](getOrCreateUser(email))
    } yield {
      logger.info(s"Logging in user ${user}")
      authPrimitives.login(
        SeeOther(
          Location(Uri.fromString("/").toOption.get)
        ).removeCookie(
          Cookie(
            "google-auth-state",
            "",
            path = Some("/")
          )
        ),
        user
      )
    }

    response.fold(
      {
        case x @ GoogleCallbackError(_) =>
          logger.error(x.toString)
          BadRequest()
        case GoogleAuthResponseIncomprehensible => InternalServerError()
      },
      identity
    ).flatten
  }
}

