package actors

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import play.api.Logger
import scala.collection.mutable.ListBuffer
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

    def getRefs(roomId: Option[Long]): List[ActorRef] = {
        var output = new ListBuffer[ActorRef]
        val message = roomId match {
            case Some(id: Long) => GetUsers(id)
            case None => GetAllUsers()
        }
        user ? message onSuccess {
            case Some(users: List[User]) => users foreach { sendTo: User =>
                // TODO Apparently this List[User] is erased by type erasure... I'm not really sure what to do about that...
                context.actorSelection(sendTo.actorPath).resolveOne onSuccess {
                    case ref: ActorRef => output += ref
                    case _ => // TODO This user doesn't exist?
                }
            }
        }
        output.toList
    }

    def receive = {
        case OpenSocket(ref: ActorRef) =>
            Logger debug s"Received an OpenSocket: $ref"
            val props = Props(new SocketActor(ref, self))
            sender ! props
        case JoinRoom(roomId: Long, userName: String) =>
            Logger debug s"Received a JoinRoom: $roomId, $userName"
            user forward JoinRoom(roomId, userName)
        case LeaveRoom(roomId: Long) =>
            Logger debug s"Received a LeaveRoom: $roomId"
            user forward LeaveRoom(roomId)
        case NameUser(userName: String, roomId: Long) =>
            Logger debug s"Received a NameUser: $userName, $roomId"
            user forward NameUser(userName, roomId)
        case PromoteUser(userName: String, roomId: Long) =>
            Logger debug s"Received a PromoteUser: $userName, $roomId"
            user forward PromoteUser(userName, roomId)
        case GetUser(actorName: String, roomId: Long) =>
            Logger debug s"Received a GetUser: $actorName, $roomId"
            user forward GetUser(actorName, roomId)
        case GetUsers(roomId: Long) =>
            Logger debug s"Received a GetUsers: $roomId"
            user forward GetUsers(roomId)
        case GetAllUsers() =>
            Logger debug s"Received a GetAllUsers"
            user forward GetAllUsers()
        case NewRoom(roomName: String, userName: String) =>
            Logger debug s"Received a NewRoom: $roomName"
            room forward NewRoom(roomName, userName)
        case NameRoom(roomId: Long, roomName: String) =>
            Logger debug s"Received a NameRoom: $roomId, $roomName"
            room forward NameRoom(roomId, roomName)
        case GetRoom(roomId: Long) =>
            Logger debug s"Received a GetRoom: $roomId"
            room forward GetRoom(roomId)
        case MessageIn(roomId: Long, messageText: String) =>
            Logger debug s"Received a MessageIn: $roomId, $messageText"
            val actorName = sender().path.name
            val socket = sender()
            // TODO It seems that every socket actor's name is "handler."
            // There's a unique number afterward, but sender.path.name doesn't give me that...
            // Should I get rid of actorname and just use actorpath?
            user ? GetUser(actorName, roomId) onSuccess {
                case Some(sentBy: User) =>
                    val option = Option(roomId)
                    getRefs(option) foreach { ref: ActorRef =>
                    ref ! MessageOut(sentBy.userName, roomId, messageText)
                }
                case None => socket ! GlobalSystemMessage(s"You aren't in the room: $roomId")
            }
        case SystemMessage(roomId: Long, messageText: String) =>
            val option = Option(roomId)
            getRefs(option) foreach { ref: ActorRef =>
                ref ! SystemMessage(roomId, messageText)
            }
        case GlobalSystemMessage(messageText: String) =>
            getRefs(None) foreach { ref: ActorRef =>
                ref ! GlobalSystemMessage(messageText)
            }
        case CloseSocket(ref: ActorRef) =>
            Logger debug s"Received a CloseSocket: $ref"
            user ! DisconnectUser(ref.path.toSerializationFormat)
    }
}