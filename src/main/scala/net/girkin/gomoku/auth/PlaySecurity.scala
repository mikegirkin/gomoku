package net.girkin.gomoku.auth

import net.girkin.gomoku.Constants
import play.api.mvc._
import net.girkin.gomoku.users.User

import scala.concurrent.{ExecutionContext, Future}

case class AuthRequest[A](
  request: Request[A],
  user: Option[User]
) extends WrappedRequest[A](request)

case class SecureRequest[A](
  request: Request[A],
  user: User
) extends WrappedRequest[A](request)

object PlaySecurity {
  import io.circe.syntax._
  import net.girkin.gomoku.global._

  implicit class ResultExtensions(val r: Result) extends AnyVal {
    def login(user: User)(implicit headers: RequestHeader) = r.addingToSession(
      Constants.UserIdSessionKey -> user.asJson.toString()
    )

    def logout()(implicit headers: RequestHeader) = r.removingFromSession(Constants.UserIdSessionKey)
  }

  def userAction(implicit ec: ExecutionContext): ActionFunction[Request, AuthRequest] = new ActionFunction[Request, AuthRequest] {
    override def invokeBlock[A](
      request: Request[A],
      block: AuthRequest[A] => Future[Result]
    ): Future[Result] = block(authInfo(request))

    override protected def executionContext: ExecutionContext = ec
  }

  def secured(notAuthorizedResult: Result)(implicit ec: ExecutionContext): ActionFunction[AuthRequest, SecureRequest] = new ActionFunction[AuthRequest, SecureRequest] {
    override def invokeBlock[A](
      request: AuthRequest[A],
      block: SecureRequest[A] => Future[Result]
    ): Future[Result] = request.user.fold {
      Future.successful { notAuthorizedResult }
    } {
      user => block(SecureRequest(request.request, user))
    }

    override protected def executionContext: ExecutionContext = ec
  }

  def ajaxSecured(implicit ec: ExecutionContext): ActionFunction[AuthRequest, SecureRequest] = secured(Results.Unauthorized)

  def authInfo[A](request: Request[A]): AuthRequest[A] = {
    AuthRequest[A](
      request,
      Constants.userExtractor(request)
    )
  }

}
