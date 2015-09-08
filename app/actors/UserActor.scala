package actors

import akka.actor._
import play.api.libs.json._
import play.api.Logger

import messages._

object UserActor {
    def props(registrar: ActorRef, out: ActorRef) = Props(new UserActor(registrar, out))
}

class UserActor(registrar: ActorRef, out: ActorRef) extends Actor {
    import UserActor._

    var name = "Anonymous12345" // TODO Get name from an actor. Send it out to the socket.
    
    // TODO 
    // Help command to tell them about available commands.
    // Ban, kick, mute, and promote commands.
    // First user into room is the creator.
    // Create can kick and ban and promote other users.
    // Promoted users can just kick, ban, and mute.. can't kick, ban, or mute creator or other promoted users.
    // Last user to leave room... room is deleted.
    // Creator can put a password on a room and change it.
    
    override def preStart() = {
        Logger.info(s"UserActor $self.path is starting up")
    }
    
    override def postStop() = {
        Logger.info(s"UserActor $self.path is shutting down")
        registrar ! Disconnect(name)
    }
    
    def receive = {
        case msg: JsValue =>
            Logger.debug(s"Received a JSON: $msg")
            try {
                (msg \ "type").get.as[String] match {
                    case "join" =>
                        Logger.debug("JSON is a join")
                        val room = (msg \ "room").get.as[String]
                        Logger.debug(s"Sending join to registrar: $name, $room")
                        registrar ! Join(room)
                    case "leave" =>
                        Logger.debug("JSON is a leave")
                        val name = (msg \ "name").get.as[String]
                        val room = (msg \ "room").get.as[String]
                        Logger.debug(s"Sending leave to registrar: $name, $room")
                        registrar ! Leave(name, room)
                    case "disconnect" =>
                        Logger.debug("JSON is a disconnect")
                        val name = (msg \ "name").get.as[String]
                        Logger.debug(s"Sending disconnect to registrar: $name")
                        registrar ! Disconnect(name)
                    case "name" =>
                        Logger.debug("JSON is a name change")
                        val name = (msg \ "name").get.as[String]
                        val room = (msg \ "room").get.as[String]
                        val change = (msg \ "change").get.as[String]
                        Logger.debug(s"Sending name change to registrar: $name, $room, $change")
                        registrar ! Name(name, room, change)
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
        case Name(name: String, room: String, change: String) =>
            Logger.debug(s"Sending a name change: $name, $room, $change")
            val json: JsValue = JsObject(Seq(
                "name" -> JsString(name),
                "room" -> JsString(room),
                "change" -> JsString(change),
                "type" -> JsString("name")
            ))
            out ! json
    }
}