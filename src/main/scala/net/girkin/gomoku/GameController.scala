package net.girkin.gomoku

import javax.inject.Inject
import play.api.mvc.{AbstractController, ControllerComponents}

import scala.concurrent.ExecutionContext

class GameController @Inject() (
  cc: ControllerComponents
)(
  implicit ec: ExecutionContext
) extends AbstractController(cc) {

  import PlayCats._
  import net.girkin.gomoku.auth.PlaySecurity._

  def webUnathorizedResponse = Redirect("/")

  def index() = (Action andThen userAction andThen secured(webUnathorizedResponse)) { implicit request =>
    Ok(views.html.game.game())
  }
}


