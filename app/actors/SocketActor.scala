package actors

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import play.api.libs.json._
import play.api.Logger
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import messages._

class SocketActor(out: ActorRef) extends Actor {
    val registrar = context.parent
    
    implicit val timeout = Timeout(5 seconds)
        
    var classUserId: BigInt = 0
    var classUserName: String = ""
    
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
                        Logger debug s"Sending JoinRoom: $roomId"
                        registrar ? JoinRoom(BigInt(roomId)) onSuccess {
                            case userId: BigInt =>
                                classUserId = userId
                                // TODO out ! JSON with the userId that the registrar assigned to this socket
                                    // Maybe just let it time out after five seconds, and then the JS on the front end can say that it timed out?
                        }
                    case "leave" =>
                        Logger debug "JSON is a leave"
                        val roomId = (msg \ "roomId").get.as[String]
                        Logger debug s"Sending LeaveRoom: $classUserId, $roomId"
                        registrar ? LeaveRoom(classUserId, BigInt(roomId)) onSuccess {
                            case updatedRows: Integer =>
                                case 1 => 
                                    // TODO Success
                                    // Case failure? Same question as above...
                                    // If it fails to parse that string as a bigint, what will happen? Should I store the IDs as a string in my
                                        // messages? I was thinking maybe I'd use a UUID/hash/whatever instead of a bigint, but then I have to 
                                        // learn how to configure the DB to generate that. Anyway, if I store the bigint as a string, I think
                                        // Maybe I'll have to catch an exception or something so that I can tell them that it failed?
                                        // Otherwise, the socket times out, and they have to get an error and reconnect. Is that an okay strategy?
                                        // I should read more about this "let it crash" ideology that I saw in the Akka docs somewhere.
                        }
                    case "name" =>
                        Logger debug "JSON is a name"
                        val userName = (msg \ "userName").get.as[String]
                        val roomId = (msg \ "roomId").get.as[String]
                        Logger debug s"Sending NameUser: $classUserId, $userName, $roomId"
                        registrar ? NameUser(classUserId, userName, BigInt(roomId)) onSuccess {
                            case userName: String => 
                                classUserName = userName
                                // TODO
                                // Maybe more logging... like "Received this username." Maybe less logging because it's getting ridiculous?
                                // out ! JSON with approved userName
                                // case failure?
                        }
                    case "message" =>
                        Logger debug s"JSON is a message"
                        val roomId = (msg \ "roomId").get.as[String]
                        val messageText = (msg \ "messageText").get.as[String]
                        Logger debug s"Sending Message: classUserId, classUserName, $roomId, $messageText"
                        registrar ! Message(classUserId, classUserName, BigInt(roomId), messageText)
                }
            } catch {
                case js: JsResultException =>
                    Logger error (s"Received a bad message: $msg", js)
                    // TODO
                    // sender ! JSON identifying a bad message
            }
        case Message(userId: BigInt, userName: String, roomId: BigInt, messageText: String) =>
            Logger debug s"Received a message: $userId, $userName, $roomId, $messageText"
            val json : JsValue = JsObject(Seq(
                "userId" -> JsString(userId.toString),
                "userName" -> JsString(userName),
                "roomId" -> JsString(roomId.toString),
                "messageText" -> JsString(messageText)
            ))
            out ! json
    }
}