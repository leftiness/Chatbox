package controllers

import play.api._
import play.api.mvc._
import play.api.Play.current
import play.api.libs.json._
import akka.actor._
import actors._


class Application extends Controller {
    
    val system = ActorSystem("system")
    val registrar = system.actorOf(Props[RegistrarActor], name = "registrar")
    
    // TODO
    // There should be a failure strategy in place where another courier gets started up if the current courier fails.
    // For now, if the courier fails, then the users won't be able to send messages. They can't just get a new ref to a new courier.
    // I was getting some deadletters earlier. I think this might be the solution.

    def index = Action {
        Ok(views.html.index())
    }

    def chat = WebSocket.acceptWithActor[JsValue, JsValue] { request => out =>
        UserActor.props(registrar, out)
    }

}
