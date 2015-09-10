package actors

import akka.actor._
import java.util.Date
import play.api.Logger
import play.api.Play.current
import scala.collection.mutable._

import messages._

class RegistrarActor extends Actor {
    val user = context actorOf Props[UserActor]
    val room = context actorOf Props[RoomActor]
    
    override def preStart() = {
        Logger info s"RegistrarActor $self.path is starting up"
    }
    
    override def postStop() = {
        Logger info s"RegistrarActor $self.path is shutting down"
    }
    
    // TODO 
    // When users leave a room, check if there are still admins in the room. If there aren't any, promote the user who has the oldest join date.
    // That joinRoom method might be confusing when I see it tomorrow. Think of it this way. The user has no ID or no name until he joins a room.
        // When he joins the room, that method returns an ID for him. That's now his ID, and it's also his name until he renames himself.
    // If the Registrar crashes, then will the DB get reset? If not, then I'll need the new registrar to empty the old DB so that they're in sync.
    // Registrar simply forwards these things between user/room/socket actors. When a SocketActor gets the result of a NameUser request back,
        // he can decide what to do with the success or failure.
    // Registrar should context.watch the socket actors... or Perhaps registrar should instantiate a SocketMonitor actor to do that job.
    
    def receive = {
        case OpenSocket(ref: ActorRef) =>
            Logger debug s"Received an OpenSocket: $ref.path"
            val socket = context actorOf Props(new SocketActor(ref))
            sender ! socket
        case JoinRoom(roomId: BigInt) =>
            Logger debug s"Received a JoinRoom: $roomId"
            user forward JoinRoom(roomId)
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
            // TODO Forward message to all users in registry
    }
}