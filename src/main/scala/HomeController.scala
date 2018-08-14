package controllers

import java.util.{Base64, UUID}

import cats.data._
import cats.implicits._
import cats.effect.IO
import javax.inject._
import play.api._
import play.api.mvc._
import org.http4s.client.blaze.Http1Client
import io.circe._


/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
trait Logging {
  val logger = Logger(this.getClass())
}

trait GomokuError
object GoogleIncomingRequestParsingError extends GomokuError
object GoogleAuthResponseIncomprehensible extends GomokuError

object global {
  type GomokuEff[A] = Either[GomokuError, A]
}

object PlayCats {
  implicit class ActionExtensions[R[_], B](val action: ActionBuilder[R, B]) extends AnyVal {
    def io(block: R[B] => IO[Result]) = action.async { implicit request =>
      block(request).unsafeToFuture()
    }
  }
}

@Singleton
class HomeController @Inject()(
  config: Configuration,
  cc: ControllerComponents)
  extends AbstractController(cc)
  with Logging
{
  import PlayCats._

  val UserIdSessionKey = "user"

  /**
   * Create an Action to render an HTML page.
   *
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */
  def index() = Action { implicit request: Request[AnyContent] =>
    val user = request.session.get(UserIdSessionKey)
    Ok(views.html.index(user))
  }

  def login() = Action { implicit request =>
    logger.info("Login page")
    Ok(views.html.login())
  }

  def logout() = Action { implicit request =>
    Redirect(routes.HomeController.index()).removingFromSession(UserIdSessionKey)
  }

  def googleAuth() = Action { implicit request =>
    val state = UUID.randomUUID().toString
    val nonce = UUID.randomUUID().toString

    Redirect("https://accounts.google.com/o/oauth2/v2/auth",
      Map[String, String](
        "client_id" -> config.get[String]("auth.google.clientId"),
        "response_type" -> "code",
        "scope" -> "openid email",
        "redirect_uri" -> s"http://localhost:9000${routes.HomeController.googleCallback()}",
        "state" -> state,
        "nonce" -> nonce
      ).mapValues(s => Seq(s))
    ).withCookies(Cookie(
      "google-auth-state",
      state,
      maxAge = Some(5 * 60),
      path = "/"
    ))
  }

  def googleCallback = Action.io { implicit request =>
    val code = Either.fromOption(
      for {
        req_state <- request.queryString.get("state").flatMap(_.headOption)
        cookie_state <- request.cookies.get("google-auth-state").map(_.value)
        result <- request.queryString.get("code").flatMap(_.headOption) if cookie_state == req_state
      } yield result,
      GoogleIncomingRequestParsingError
    )

    val response: EitherT[IO, GomokuError, Result] = for {
      code <- EitherT.fromEither[IO](code)
      userData <- EitherT.liftF[IO, GomokuError, Json](requestUserData(code))
      email <- {
        EitherT.fromEither[IO](userData.hcursor.get[String]("email").leftMap[GomokuError](
          _ => GoogleAuthResponseIncomprehensible))
      }
    } yield {
      logger.info(s"Logging in user ${email}")
      Redirect(routes.HomeController.index()).withSession(
        UserIdSessionKey -> email
      )
    }

    response.fold(
      {
        case GoogleIncomingRequestParsingError => BadRequest
        case GoogleAuthResponseIncomprehensible => InternalServerError
      },
      identity
    ).map { result =>
      result.discardingCookies(DiscardingCookie("google-auth-state"))
    }
  }

  def requestUserData(code: String) = {
    import org.http4s._
    import org.http4s.client._
    import org.http4s.client.dsl.io._
    import org.http4s.circe.CirceEntityCodec._
    import org.http4s.Method._
    import io.circe.generic.extras.Configuration
    import io.circe.generic.extras.semiauto._

    case class GoogleUserResponse(tokenType: String, idToken: String)

    implicit val snakeCaseConfig = Configuration.default.withSnakeCaseMemberNames
    implicit val decoder = deriveDecoder[GoogleUserResponse]

    val client: Client[IO] = Http1Client[IO]().unsafeRunSync()
    val requestBody = Seq(
      "code" -> code,
      "client_id" -> config.get[String]("auth.google.clientId"),
      "client_secret" -> config.get[String]("auth.google.clientSecret"),
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


}
