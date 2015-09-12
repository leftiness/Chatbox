package controllers

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import play.api.libs.json._
import play.api.Logger
import play.api.mvc._
import play.api.Play.current
import scala.concurrent.duration._

import scala.concurrent.Await

import actors._

class Application extends Controller {
    
    val system = ActorSystem("system")
    val registrar = system actorOf Props[RegistrarActor]
    
    implicit val timeout = Timeout(5 seconds)
    
    Logger info "Application is starting up"
    
    // TODO There should be a failure strategy in place where another registrar gets started up if the current one fails.

    def index = Action {
        Logger debug "Received request for /"
        Ok(views.html.index())
    }

    def chat = WebSocket.acceptWithActor[JsValue, JsValue] { request => out =>
        Logger debug "Received request for /chat"
        val future = registrar ? messages.OpenSocket(out)
        Await.result(future, timeout.duration).asInstanceOf[Props]
        // TODO Can I do something asynchronous here instead of blocking with Await?
    }

}
