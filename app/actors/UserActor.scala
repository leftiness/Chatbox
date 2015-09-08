package actors

import scala.concurrent._
import scala.concurrent.duration._
import ExecutionContext.Implicits.global
import play.api.libs.json._
import play.api.Logger
import akka.actor._
import akka.util.Timeout
import messages._

object UserActor {
    def props(registrar: ActorRef, out: ActorRef) = Props(new UserActor(registrar, out))
}

class UserActor(registrar: ActorRef, out: ActorRef) extends Actor {
    import UserActor._

    var name = "Anonymous12345" // TODO Get name from an actor. Send it out to the socket.
    
    override def preStart() = {
        Logger.info(s"UserActor $self.path is starting up")
    }
    
    override def postStop() = {
        Logger.info(s"UserActor $self.path is shutting down")
    }
    
    def receive = {
        case msg: JsValue =>
            Logger.debug(s"Received a JSON: $msg")
            try {
                (msg \ "type").get.as[String] match {
                    case "message" =>
                        Logger.debug(s"JSON is a message")
                        val name = (msg \ "name").get.as[String]
                        val room = (msg \ "room").get.as[String]
                        val message = (msg \ "message").get.as[String]
                        Logger.debug(s"Sending message to registrar: $name, $room, $message")
                        registrar ! Message(name, room, message)
                }
            } catch {
                case js: JsResultException =>
                    Logger.error(s"Received a bad message: $msg", js)
                    // sender ! JSON identifying a bad message
            }
        case Message(name: String, room: String, message: String) =>
            Logger.debug(s"Sending a message: $name, $room, $message")
            val json: JsValue = JsObject(Seq(
                "name" -> JsString(name),
                "room" -> JsString(room),
                "message" -> JsString(message),
                "type" -> JsString("message")
            ))
            out ! json
            val json: JsValue = JsObject(Seq(
                "name" -> JsString(name),
                "room" -> JsString(room),
                "message" -> JsString(message)
            ))
            out ! json
    }
}