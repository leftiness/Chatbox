package actors

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import play.api.Logger
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import messages._

class RegistrarActor extends Actor {
    val user = context actorOf Props[UserActor]
    val room = context actorOf Props[RoomActor]

    implicit val timeout = Timeout(5.seconds)
    
    override def preStart() = {
        Logger info s"RegistrarActor $self is starting up"
    }
    
    override def postStop() = {
        Logger info s"RegistrarActor $self is shutting down"
    }

    def receive = {
        case OpenSocket(ref: ActorRef) =>
            Logger debug s"Received an OpenSocket: $ref"
            val props = Props(new SocketActor(ref, self))
            sender ! props
        case JoinRoom(roomId: String) =>
            Logger debug s"Received a JoinRoom: $roomId"
            user forward JoinRoom(roomId)
        case LeaveRoom(roomId: String) =>
            Logger debug s"Received a LeaveRoom: $roomId"
            user forward LeaveRoom(roomId)
        case NameUser(userName: String, roomId: String) =>
            Logger debug s"Received a NameUser: $userName, $roomId"
            user forward NameUser(userName, roomId)
        case PromoteUser(userName: String, roomId: String) =>
            Logger debug s"Received a PromoteUser: $userName, $roomId"
            user forward PromoteUser(userName, roomId)
        case NewRoom(roomName: String) =>
            Logger debug s"Received a NewRoom: $roomName"
            room forward NewRoom(roomName)
        case NameRoom(roomId: String, roomName: String) =>
            Logger debug s"Received a NameRoom: $roomId, $roomName"
            room forward NameRoom(roomId, roomName)
        case MessageIn(roomId: String, messageText: String) =>
            Logger debug s"Received a MessageIn: $roomId, $messageText"
            val path = sender().path.toSerializationFormat
            user ? GetUser(path, roomId) onSuccess {
                case Some(sentBy: User) => user ? GetUsers(roomId) onSuccess {
                    case Some(users: List[User]) => users foreach { sendTo: User =>
                        // TODO Apparently this List[User] is erased by type erasure... I'm not really sure what to do about that...
                        context.actorSelection(sendTo.actorPath).resolveOne onSuccess {
                            case ref: ActorRef => ref ! MessageOut(sentBy.userName, roomId, messageText)
                            case _ => // TODO Could not find an actor with that path
                        }
                    }
                }
            }
        case SystemMessage(roomId: String, messageText: String) =>
            // TODO get users in the room and send them a message
        case GlobalSystemMessage(messageText: String) =>
            // TODO send message to all users in registry
        case CloseSocket(ref: ActorRef) =>
            Logger debug s"Received a Terminated: $ref"
            user ! DisconnectUser(ref.path.toSerializationFormat)
    }
}