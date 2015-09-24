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
        case JoinRoom(roomId: String, userName: String) =>
            Logger debug s"Received a JoinRoom: $roomId, $userName"
            user forward JoinRoom(roomId, userName)
        case LeaveRoom(roomId: String) =>
            Logger debug s"Received a LeaveRoom: $roomId"
            user forward LeaveRoom(roomId)
        case NameUser(userName: String, roomId: String) =>
            Logger debug s"Received a NameUser: $userName, $roomId"
            user forward NameUser(userName, roomId)
        case PromoteUser(userName: String, roomId: String) =>
            Logger debug s"Received a PromoteUser: $userName, $roomId"
            user forward PromoteUser(userName, roomId)
        case GetUser(actorName: String, roomId: String) =>
            Logger debug s"Received a GetUser: $actorName, $roomId"
            user forward GetUser(actorName, roomId)
        case GetUsers(roomId: String) =>
            Logger debug s"Received a GetUsers: $roomId"
            user forward GetUsers(roomId)
        case GetAllUsers() =>
            Logger debug s"Received a GetAllUsers"
            user forward GetAllUsers()
        case NewRoom(roomName: String) =>
            Logger debug s"Received a NewRoom: $roomName"
            room forward NewRoom(roomName)
        case NameRoom(roomId: String, roomName: String) =>
            Logger debug s"Received a NameRoom: $roomId, $roomName"
            room forward NameRoom(roomId, roomName)
        case GetRoom(roomId: String) =>
            Logger debug s"Received a GetRoom: $roomId"
            room forward GetRoom(roomId)
        case MessageIn(roomId: String, messageText: String) =>
            Logger debug s"Received a MessageIn: $roomId, $messageText"
            val actorName = sender().path.name
            val socket = sender()
            user ? GetUser(actorName, roomId) onSuccess {
                case Some(sentBy: User) => user ? GetUsers(roomId) onSuccess {
                    case Some(users: List[User]) => users foreach { sendTo: User =>
                        // TODO Apparently this List[User] is erased by type erasure... I'm not really sure what to do about that...
                        context.actorSelection(sendTo.actorPath).resolveOne onSuccess {
                            case ref: ActorRef => ref ! MessageOut(sentBy.userName, roomId, messageText)
                            case _ => // TODO Could not find an actor with that path
                        }
                    }
                }
                case None => socket ! GlobalSystemMessage(s"You aren't in the room: $roomId")
            }
        case SystemMessage(roomId: String, messageText: String) =>
            user ? GetUsers(roomId) onSuccess {
                case Some(users: List[User]) => users foreach { sendTo: User =>
                    // TODO This List[User] probably is also getting erased...
                    context.actorSelection(sendTo.actorPath).resolveOne onSuccess {
                        case ref: ActorRef => ref ! SystemMessage(roomId, messageText)
                        case _ => // TODO Could not find an actor with that path...
                    }
                }
                case None => // TODO There were no users in the room?
            }
        case GlobalSystemMessage(messageText: String) =>
            user ? GetAllUsers() onSuccess {
                case Some(users: List[User]) => users foreach { sendTo: User =>
                    context.actorSelection(sendTo.actorPath).resolveOne onSuccess {
                        case ref: ActorRef => ref ! GlobalSystemMessage(messageText)
                        case _ => // TODO Could not find an actor with that path...
                    }
                }
                case None => // TODO There were no users at all?
            }
        case CloseSocket(ref: ActorRef) =>
            Logger debug s"Received a CloseSocket: $ref"
            user ! DisconnectUser(ref.path.toSerializationFormat)
    }
}