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
    
    override def preStart() = {
        Logger info s"RoomActor $self.path is starting up"
    }
    
    override def postStop() = {
        Logger info s"RoomActor $self.path is shutting down"
    }
    
    object Room {
        val parser: RowParser[Room] = {
            get[BigInt]("rooms.id") ~
            str("rooms.name") map {
                case id ~ name => messages.Room(id, name)
            }
        }
    }
    
    def newRoom(roomName: String): Option[BigInt] = {
        Logger debug s"Creating new room: $roomName"
        DB.withConnection { implicit c =>
            return SQL"insert into rooms (name) values ($roomName)"
                .executeInsert(get[BigInt]("id").singleOpt)
        }
    }
    
    def getRoom(roomId: BigInt): Option[Room] = {
        Logger debug s"Retrieving room: $roomId"
        DB.withConnection { implicit c =>
            return SQL"select (id) from rooms where id = '$roomId'"
                .as(Room.parser.singleOpt)
        }
    }
    
    def nameRoom(roomId: BigInt, roomName: String): Integer = {
        Logger debug s"Renaming room: $roomId, $roomName"
        DB.withConnection { implicit c =>
            return SQL"update rooms set name = '$roomName' where id = '$roomId'"
                .executeUpdate()
        }
    }
    
    def deleteRoom(roomId: BigInt): Integer = {
        Logger debug (s"Deleting room: $roomId")
        DB.withConnection { implicit c =>
            return SQL"delete from rooms where id = '$roomId'"
                    .executeUpdate()
        }
    }
    
    def receive = {
        case NewRoom(roomName: String) =>
            Logger debug s"Received a NewRoom: $roomName"
            sender ! newRoom(roomName)
        case GetRoom(roomId: BigInt) =>
            Logger debug s"Received a GetRoom: $roomId"
            sender ! getRoom(roomId)
        case NameRoom(roomId: BigInt, roomName: String) =>
            Logger debug s"Received a NameRoom: $roomId, $roomName"
            sender ! nameRoom(roomId, roomName)
        case DeleteRoom(roomId: BigInt) =>
            Logger debug s"Received a DeleteRoom: $roomId"
            sender ! deleteRoom(roomId)
    }
}