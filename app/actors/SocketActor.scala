package actors

import akka.actor._
import play.api.libs.json._
import play.api.Logger

import messages._

class SocketActor(out: ActorRef, registrar: ActorRef) extends Actor {

    override def preStart() = {
        Logger info s"SocketActor $self is starting up"
    }
    
    override def postStop() = {
        Logger info s"SocketActor $self is shutting down"
        registrar ! CloseSocket(self)
    }
    
    def receive = {
        case msg: JsValue =>
            Logger debug s"Received a JSON: $msg"
            try {
                (msg \ "messageType").get.as[String] match {
                    case "newRoom" =>
                        Logger debug "Json is a newRoom"
                        val roomName = (msg \ "roomName").get.as[String]
                        val userName = (msg \ "userName").get.as[String]
                        registrar ! NewRoom(roomName, userName)
                    case "joinRoom" =>
                        Logger debug "JSON is a join"
                        val roomId = (msg \ "roomId").get.as[String]
                        val userName = (msg \ "userName").get.as[String]
                        registrar ! JoinRoom(roomId, userName)
                    case "leaveRoom" =>
                        Logger debug "JSON is a leave"
                        val roomId = (msg \ "roomId").get.as[String]
                        registrar ! LeaveRoom(roomId)
                    case "disconnectUser" =>
                        Logger debug "Json is a leave"
                        registrar ! DisconnectUser(self.path.name)
                    case "nameUser" =>
                        Logger debug "JSON is a name"
                        val userName = (msg \ "userName").get.as[String]
                        val roomId = (msg \ "roomId").get.as[String]
                        registrar ! NameUser(userName, roomId)
                    case "promoteUser" =>
                        Logger debug "JSON is a promoteUser"
                        val userName = (msg \ "userName").get.as[String]
                        val roomId = (msg \ "roomId").get.as[String]
                        registrar ! PromoteUser(userName, roomId)
                    case "messageIn" =>
                        Logger debug s"JSON is a message"
                        val roomId = (msg \ "roomId").get.as[String]
                        val messageText = (msg \ "messageText").get.as[String]
                        registrar ! MessageIn(roomId, messageText)
                }
            } catch {
                case js: JsResultException =>
                    Logger error (s"Received a bad message: $msg", js)
                    val json: JsValue = JsObject(Seq(
                        "messageText" -> JsString(Json.stringify(msg)),
                        "messageType" -> JsString("badMessage")
                    ))
                    out ! json
            }
        case MessageOut(userName: String, roomId: String, messageText: String) =>
            Logger debug s"Received a MessageOut: $userName, $roomId, $messageText"
            val json: JsValue = JsObject(Seq(
                "userName" -> JsString(userName),
                "roomId" -> JsString(roomId),
                "messageText" -> JsString(messageText),
                "messageType" -> JsString("messageOut")
            ))
            out ! json
        case NameUser(userName: String, roomId: String) =>
            Logger debug s"Received a NameUser: $userName, $roomId"
            val json: JsValue = JsObject(Seq(
                "userName" -> JsString(userName),
                "roomId" -> JsString(roomId),
                "messageType" -> JsString("nameUser")
            ))
            out ! json
        case SystemMessage(roomId: String, messageText: String) =>
            Logger debug s"Received a SystemMessage: $roomId, $messageText"
            val json: JsValue = JsObject(Seq(
                "roomId" -> JsString(roomId),
                "messageText" -> JsString(messageText),
                "messageType" -> JsString("systemMessage")
            ))
            out ! json
        case GlobalSystemMessage(messageText: String) =>
            Logger debug s"Received a GlobalSystemMessage: $messageText"
            val json: JsValue = JsObject(Seq(
                "messageText" -> JsString(messageText),
                "messageType" -> JsString("globalSystemMessage")
            ))
            out ! json
    }
}