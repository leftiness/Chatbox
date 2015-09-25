package actors

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import anorm._
import anorm.SqlParser._
import play.api.db._
import play.api.Logger
import play.api.Play.current
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import messages._

class RoomActor() extends Actor {
    val registrar = context.parent
    val maxRoomNameLength = 255

    implicit val timeout = Timeout(5.seconds)
    
    override def preStart() = {
        Logger info s"RoomActor $self is starting up"
    }
    
    override def postStop() = {
        Logger info s"RoomActor $self is shutting down"
    }
    
    object Room {
        val parser: RowParser[Room] = {
            str("ROOMS.ROOM_ID") ~
            str("ROOMS.ROOM_NAME") map {
                case roomId ~ roomName => messages.Room(roomId, roomName)
            }
        }
    }
    
    def newRoom(roomName: String): Option[String] = {
        // TODO Use a string hash instead of an incrementing bigint for room ids
        Logger debug s"Creating new room: $roomName"
        DB.withConnection { implicit c =>
            return SQL"INSERT INTO ROOMS (ROOM_NAME) values ($roomName)"
                .executeInsert(str("ROOMS.ROOM_ID").singleOpt)
        }
    }
    
    def getRoom(roomId: String): Option[Room] = {
        Logger debug s"Retrieving room: $roomId"
        DB.withConnection { implicit c =>
            return SQL"SELECT (ROOM_ID) FROM ROOMS WHERE ROOM_ID = $roomId"
                .as(Room.parser.singleOpt)
        }
    }
    
    def nameRoom(roomId: String, roomName: String): Int = {
        Logger debug s"Renaming room: $roomId, $roomName"
        DB.withConnection { implicit c =>
            return SQL"UPDATE ROOMS SET ROOM_NAME = $roomName WHERE ROOM_ID = $roomId"
                .executeUpdate()
        }
    }
    
    def deleteRoom(roomId: String): Int = {
        Logger debug s"Deleting room: $roomId"
        DB.withConnection { implicit c =>
            return SQL"DELETE FROM ROOMS WHERE ROOM_ID = $roomId"
                    .executeUpdate()
        }
    }
    
    def receive = {
        case NewRoom(roomName: String, userName: String) =>
            Logger debug s"Received a NewRoom: $roomName"
            val validName = roomName.substring(0, maxRoomNameLength)
            newRoom(validName) match {
                case Some(roomId: String) =>
                    registrar forward JoinRoom(roomId, userName)
                    registrar ! SystemMessage(roomId, s"Room $validName has been created")
                    sender ! NameRoom(roomId, validName)
                case None => sender ! GlobalSystemMessage(s"Failed to create room $validName")
            }
        case GetRoom(roomId: String) =>
            Logger debug s"Received a GetRoom: $roomId"
            sender ! getRoom(roomId)
        case NameRoom(roomId: String, roomName: String) =>
            Logger debug s"Received a NameRoom: $roomId, $roomName"
            val validName = roomName.substring(0, maxRoomNameLength)
            val ref = sender()
            registrar ? GetUser(ref.path.name, roomId) onSuccess {
                case Some(user: User) => user.isAdmin match {
                    case true => nameRoom(roomId, validName) match {
                        case 0 => ref ! SystemMessage(roomId, s"Failed to rename room $roomId to $validName")
                        case _ => registrar ! SystemMessage(roomId, s"Name was renamed to $validName")
                    }
                    case false => ref ! SystemMessage(roomId, s"Only admins can name the room")
                }
            }
            nameRoom(roomId, validName) match {
                case 0 => registrar ! SystemMessage
            }
        case DeleteRoom(roomId: String) =>
            Logger debug s"Received a DeleteRoom: $roomId"
            sender ! deleteRoom(roomId)
    }
}