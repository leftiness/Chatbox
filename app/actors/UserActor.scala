package actors

import scala.concurrent._
import scala.concurrent.duration._
import ExecutionContext.Implicits.global
import play.api.libs.json._
import akka.actor._
import akka.util.Timeout
import messages._

object UserActor {
    def props(courier: ActorRef, out: ActorRef) = Props(new UserActor(courier, out))
}

class UserActor(courier: ActorRef, out: ActorRef) extends Actor {
    import UserActor._
    
    val system = ActorSystem("system")
    var name = "Anonymous12345" // TODO Get name from an actor. Send it out to the socket.
    
    override def postStop() = {
        courier ! Disconnect(name)  
    }
    
    def receive = {
        case msg: JsValue =>
            try {
                val name = (msg \ "name").get.as[String]
                val message = (msg \ "message").get.as[String]
                val room = (msg \ "room").get.as[String]
                courier ! Message(name, room, message)
            } catch {
                case json: JsResultException =>
                    // sender ! JSON identifying a bad message
            }
        case Message(name: String, room: String, message: String) =>
            val json: JsValue = JsObject(Seq(
                "name" -> JsString(name),
                "room" -> JsString(room),
                "message" -> JsString(message)
            ))
            out ! json
    }
}