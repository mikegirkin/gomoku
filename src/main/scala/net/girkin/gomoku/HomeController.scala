package net.girkin.gomoku

import cats.effect.IO
import io.circe.Encoder
import io.circe.syntax._
import javax.inject._
import users.User

import scala.concurrent.{ExecutionContext, Future}

//trait Logging {
//  val logger = Logger(this.getClass())
//}

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

//
//class LoggingAction @Inject() (parser: BodyParsers.Default)(implicit ec: ExecutionContext)
//  extends ActionBuilderImpl(parser) with Logging {
//  override def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]) = {
//    logger.info(request.toString())
//    block(request)
//  }
//}

@Singleton
class HomeController @Inject()
// (
//  google: GoogleAuthImpl,
//  loggingAction: LoggingAction,
//  config: Configuration,
//  cc: ControllerComponents)(
//  implicit ec: ExecutionContext
//) extends AbstractController(cc)
//  with Logging
//{
//  import PlayCats._
//  import auth.Security._
//
//  def index() = loggingAction { implicit request: Request[AnyContent] =>
//    val userStr = Constants.userExtractor(request).map(_.toString)
//    Ok(views.html.index(userStr))
//  }
//
//  def test() = (loggingAction andThen userAction andThen secured(Redirect(routes.HomeController.index()))).io { request =>
//    IO {
//      Ok(s"It works: ${request.user}")
//    }
//  }
//
//  def login() = loggingAction { implicit request =>
//    logger.info("Login page")
//    Ok(views.html.login())
//  }
//
//  def logout() = loggingAction { implicit request =>
//    Redirect(routes.HomeController.index()).logout()
//  }
//
//  def googleAuth() = loggingAction { google.startGoogleAuth(s"http://localhost:9000${routes.HomeController.googleCallback()}") }
//
//  def googleCallback() = loggingAction.io { google.googleCallback }
//
//}
