package actors

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import play.api.Logger
import scala.concurrent.duration._

import messages._

class RegistrarActor extends Actor {
    val user = context actorOf Props[UserActor]
    val room = context actorOf Props[RoomActor]

    implicit val timeout = Timeout(5.seconds)
    
    override def preStart() = {
        Logger info s"RegistrarActor $self.path is starting up"
    }
    
    override def postStop() = {
        Logger info s"RegistrarActor $self.path is shutting down"
    }

    def receive = {
        case OpenSocket(ref: ActorRef) =>
            Logger debug s"Received an OpenSocket: $ref.path"
            val socket = context actorOf Props(new SocketActor(ref))
            context watch socket
            sender ! socket
        case JoinRoom(roomId: BigInt, actorPath: String) =>
            Logger debug s"Received a JoinRoom: $roomId, $actorPath"
            user forward JoinRoom(roomId, actorPath)
        case LeaveRoom(userId: BigInt, roomId: BigInt) =>
            Logger debug s"Received a LeaveRoom: $userId, $roomId"
            user forward LeaveRoom(userId, roomId)
        case GetUser(userId: BigInt) =>
            Logger debug s"Received a GetUser: $userId"
            user forward GetUser(userId)
        case GetUsers(roomId: BigInt) =>
            Logger debug s"Received a GetUsers: $roomId"
            user forward GetUsers(roomId)
        case NameUser(userId: BigInt, userName: String, roomId: BigInt) =>
            Logger debug s"Received a NameUser: $userId, $userName, $roomId"
            user forward NameUser(userId, userName, roomId)
        case PromoteUser(userId: BigInt) =>
            Logger debug s"Received a PromoteUser: $userId"
            user forward PromoteUser(userId)
        case BanUser(userId: BigInt) =>
            Logger debug s"Received a BanUser: $userId"
            user forward BanUser(userId)
        case NewRoom(roomName: String) =>
            Logger debug s"Received a NewRoom: $roomName"
            room forward NewRoom(roomName)
        case GetRoom(roomId: BigInt) =>
            Logger debug s"Received a GetRoom: $roomId"
            room forward GetRoom(roomId)
        case NameRoom(roomId: BigInt, roomName: String) =>
            Logger debug s"Received a NameRoom: $roomId, $roomName"
            room forward NameRoom(roomId, roomName)
        case DeleteRoom(roomId: BigInt) =>
            Logger debug s"Received a DeleteRoom: $roomId"
            room forward DeleteRoom(roomId)
        case Message(userId: BigInt, userName: String, roomId: BigInt, messageText: String) =>
            Logger debug s"Received a message: $userId, $userName, $roomId, $messageText"
            user ? GetUsers(roomId) onSuccess {
                case Some(users: List[User]) => users foreach { user: User =>
                    context.actorSelection(user.actorPath).resolveOne map { ref: ActorRef =>
                        ref ! Message(userId, userName, roomId, messageText)
                    }
                }
            }
        case Terminated(ref: ActorRef) =>
            Logger debug s"Received a Terminated: $ref.path"
            user ! DeleteUser(ref.path.toSerializationFormat)
    }
}