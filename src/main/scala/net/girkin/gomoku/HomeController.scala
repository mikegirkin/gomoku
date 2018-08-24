package net.girkin.gomoku

import auth.GoogleAuthImpl
import cats.effect.IO
import javax.inject._
import play.api._
import play.api.mvc._
import users.User

import scala.concurrent.{ExecutionContext, Future}

trait Logging {
  val logger = Logger(this.getClass())
}

trait GomokuError
object GoogleIncomingRequestParsingError extends GomokuError
object GoogleAuthResponseIncomprehensible extends GomokuError

object global {
  import io.circe._
  import io.circe.generic.semiauto._
  import io.circe.java8.time._

  type GomokuEff[A] = Either[GomokuError, A]

  implicit val userEncoder: Encoder[User] = deriveEncoder[User]
  implicit val userDecoder: Decoder[User] = deriveDecoder[User]
}

object PlayCats extends Logging {

  implicit class ActionExtensions[R[_], B](val actionBuilder: ActionBuilder[R, B]) extends AnyVal {
    def io(block: R[B] => IO[Result]) = actionBuilder.async { implicit request =>
      block(request).unsafeToFuture()
    }
  }
}

object Constants {
  import global._
  import io.circe.parser._

  val UserIdSessionKey = "user"

  def userExtractor[A](request: Request[A]): Option[User] = for {
    sessionValue <- request.session.get(UserIdSessionKey)
    user <- decode[User](sessionValue).toOption
  } yield user
}

class LoggingAction @Inject() (parser: BodyParsers.Default)(implicit ec: ExecutionContext)
  extends ActionBuilderImpl(parser) with Logging {
  override def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]) = {
    logger.info(request.toString())
    block(request)
  }
}

@Singleton
class HomeController @Inject()(
  google: GoogleAuthImpl,
  loggingAction: LoggingAction,
  config: Configuration,
  cc: ControllerComponents)(
  implicit ec: ExecutionContext
) extends AbstractController(cc)
  with Logging
{
  import PlayCats._
  import auth.PlaySecurity._

  def index() = loggingAction { implicit request: Request[AnyContent] =>
    val userStr = Constants.userExtractor(request).map(_.toString)
    Ok(views.html.index(userStr))
  }

  def test() = (loggingAction andThen userAction andThen secured(Redirect(routes.HomeController.index()))).io { request =>
    IO {
      Ok(s"It works: ${request.user}")
    }
  }

  def login() = loggingAction { implicit request =>
    logger.info("Login page")
    Ok(views.html.login())
  }

  def logout() = loggingAction { implicit request =>
    Redirect(routes.HomeController.index()).logout()
  }

  def googleAuth() = loggingAction { google.startGoogleAuth(s"http://localhost:9000${routes.HomeController.googleCallback()}") }

  def googleCallback() = loggingAction.io { google.googleCallback }

}
