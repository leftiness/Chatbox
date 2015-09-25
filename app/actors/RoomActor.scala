package actors

import akka.actor._
import anorm._
import anorm.SqlParser._
import play.api.db._
import play.api.Logger
import play.api.Play.current

import messages._

class RoomActor() extends Actor {
    val registrar = context.parent
    val maxRoomNameLength = 255
    
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
    
    def newRoom(roomName: String): Option[Long] = {
        // TODO Use a string hash instead of an incrementing bigint for room ids
        Logger debug s"Creating new room: $roomName"
        DB.withConnection { implicit c =>
            return SQL"INSERT INTO ROOMS (ROOM_NAME) values ($roomName)"
                .executeInsert(long("ROOMS.ROOM_ID").singleOpt)
        }
    }
    
    def getRoom(roomId: String): Option[Room] = {
        Logger debug s"Retrieving room: $roomId"
        DB.withConnection { implicit c =>
            return SQL"SELECT (ROOM_ID) FROM ROOMS WHERE ROOM_ID = $roomId"
                .as(Room.parser.singleOpt)
        }
    }
    
    def nameRoom(roomId: String, roomName: String): Integer = {
        Logger debug s"Renaming room: $roomId, $roomName"
        DB.withConnection { implicit c =>
            return SQL"UPDATE ROOMS SET ROOM_NAME = $roomName WHERE ROOM_ID = $roomId"
                .executeUpdate()
        }
    }
    
    def deleteRoom(roomId: String): Integer = {
        Logger debug s"Deleting room: $roomId"
        DB.withConnection { implicit c =>
            return SQL"DELETE FROM ROOMS WHERE ROOM_ID = $roomId"
                    .executeUpdate()
        }
    }
    
    def receive = {
        case NewRoom(roomName: String) =>
            Logger debug s"Received a NewRoom: $roomName"
            val validName = roomName.substring(0, maxRoomNameLength)
            sender ! newRoom(validName)
        case GetRoom(roomId: String) =>
            Logger debug s"Received a GetRoom: $roomId"
            sender ! getRoom(roomId)
        case NameRoom(roomId: String, roomName: String) =>
            Logger debug s"Received a NameRoom: $roomId, $roomName"
            val validName = roomName.substring(0, maxRoomNameLength)
            sender ! nameRoom(roomId, validName)
        case DeleteRoom(roomId: String) =>
            Logger debug s"Received a DeleteRoom: $roomId"
            sender ! deleteRoom(roomId)
    }
}