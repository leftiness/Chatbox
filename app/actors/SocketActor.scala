package actors

import akka.actor._
import play.api.libs.json._
import play.api.Logger

import messages._

class SocketActor(out: ActorRef) extends Actor {
    val registrar = context.parent
    
    override def preStart() = {
        Logger info s"SocketActor $self.path is starting up"
    }
    
    override def postStop() = {
        Logger info s"SocketActor $self.path is shutting down"
    }
    
    def receive = {
        case msg: JsValue =>
            Logger debug s"Received a JSON: $msg"
            try {
                (msg \ "type").get.as[String] match {
                    case "join" =>
                        Logger debug "JSON is a join"
                        val roomId = (msg \ "roomId").get.as[String]
                        registrar ! JoinRoom(roomId)
                    case "leave" =>
                        Logger debug "JSON is a leave"
                        val roomId = (msg \ "roomId").get.as[String]
                        registrar ! LeaveRoom(roomId)
                            // TODO Case failure?
                    case "name" =>
                        Logger debug "JSON is a name"
                        val userName = (msg \ "userName").get.as[String]
                        val roomId = (msg \ "roomId").get.as[String]
                        registrar ! NameUser(userName, roomId)
                    case "message" =>
                        Logger debug s"JSON is a message"
                        val roomId = (msg \ "roomId").get.as[String]
                        val messageText = (msg \ "messageText").get.as[String]
                        registrar ! MessageIn(roomId, messageText)
                }
            } catch {
                case js: JsResultException =>
                    Logger error (s"Received a bad message: $msg", js)
                    // TODO
                    // sender ! JSON identifying a bad message
            }
        case MessageOut(userName: String, roomId: String, messageText: String) =>
            Logger debug s"Received a MessageOut: $userName, $roomId, $messageText"
            val json : JsValue = JsObject(Seq(
                "userName" -> JsString(userName),
                "roomId" -> JsString(roomId),
                "messageText" -> JsString(messageText)
            ))
            out ! json
    }
}