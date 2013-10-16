package controllers
import play.api.libs.ws.{WS, Response}
import scala.concurrent.Future
import play.api.mvc.{Action, Controller}
import play.api.libs.concurrent.Execution.Implicits._

object Application extends Controller {

  def index = Action {
    Async {
        WS.url("http://www.google.com").get().map { response =>
          Ok("got back " + response.body)
        }
    }
  }
}