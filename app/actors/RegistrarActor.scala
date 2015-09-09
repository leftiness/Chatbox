package actors

import akka.actor._
import anorm._
import anorm.SqlParser._
import java.util.Date
import play.api.db._
import play.api.Logger
import play.api.Play.current
import scala.collection.mutable._
import scala.math.BigInt

import messages._

case class Room(id: BigInt, name: String)
case class User(id: BigInt, room: BigInt, name: String, joined: Date, admin: Boolean, banned: Boolean)

object Room {
    val parser: RowParser[Room] = {
        get[BigInt]("rooms.id") ~
        str("rooms.name") map {
            case id ~ name => Room(id, name)
        }
    }
}

object User {
    val parser: RowParser[User] = {
        get[BigInt]("users.id") ~
        get[BigInt]("users.room") ~
        str("users.name") ~
        date("users.joined") ~
        bool("users.admin") ~
        bool("users.banned") map {
            case user ~ room ~ name ~ joined ~ admin ~ banned =>
                User(user, room, name, joined, admin, banned)
        }
    }
}

class RegistrarActor extends Actor {
    val registry = new HashMap[String, Map[String, ActorRef]]()
    val registrar = "Registrar"
    
    override def preStart() = {
        Logger.info(s"RegistrarActor $self.path is starting up")
    }
    
    override def postStop() = {
        Logger.info(s"RegistrarActor $self.path is shutting down")
    }
    
    def userJoined(name: String): String = {
        return s"$name has joined the room"
    }
    
    def userLeft(name: String): String = {
        return s"$name has left the room"
    }
    
    def userChangedName(name: String, change: String): String = {
        return s"$name is now known as $change"
    }
    
    def nameIsTaken(name: String): String = {
        return s"The name $name is already taken"
    }
    
    def newRoom(name: String): Option[BigInt] = {
        Logger.debug(s"Creating new room: $name")
        DB.withConnection { implicit c =>
            return SQL"insert into rooms (name) values (name)"
                .executeInsert(get[BigInt]("id").singleOpt)
        }
    }
    
    def getRoom(id: BigInt): Option[Room] = {
        Logger.debug(s"Retrieving room: $id")
        DB.withConnection { implicit c =>
            return SQL"select (id, name) from rooms where id = '$id'"
                .as(Room.parser.singleOpt)
        }
    }
    
    def nameRoom(id: BigInt, name: String): Integer = {
        Logger.debug(s"Renaming room: $id, $name")
        DB.withConnection { implicit c =>
            return SQL"update rooms set name = '$name' where id = '$id'"
                .executeUpdate()
        }
    }
    
    def deleteRoom(id: BigInt): Integer = {
        Logger.debug(s"Deleting room: $id")
        DB.withConnection { implicit c =>
            return SQL"delete from rooms where id = '$id'"
                    .executeUpdate()
        }
    }
    
    def joinRoom(id: BigInt): Option[BigInt] = {
        Logger.debug(s"Joining room: $id")
        DB.withConnection { implicit c =>
            return SQL"insert into users (room) values ('$id')"
                .executeInsert(get[BigInt]("id").singleOpt)
        }
    }
    
    def leaveRoom(user: BigInt, room: BigInt): Integer = {
        Logger.debug(s"Leaving room: $user, $room")
        DB.withConnection { implicit c =>
            return SQL"delete from users where id = '$user' and room = '$room'"
                .executeUpdate()
        }
    }
    
    def getUser(id: BigInt): Option[User] = {
        Logger.debug(s"Getting user: $id")
        DB.withConnection { implicit c =>
            return SQL"""select (id, room, name, joined, admin, banned) 
                from users
                where id = '$id'
                """
                .as(User.parser.singleOpt)
        }
    }
    
    def getUsers(room: BigInt): List[User] = {
        Logger.debug(s"Getting users in room: $room")
        DB.withConnection { implicit c =>
            return SQL"""select (id, room, name, joined, admin, banned)
                from users
                where room = '$room'
                """
                .as(User.parser.*)
        }
    }
    
    def nameUser(id: BigInt, name: String): Integer = {
        Logger.debug(s"Renaming user: $id, $name")
        DB.withConnection { implicit c =>
            return SQL"update users set name = '$name' where id = '$id'"
                .executeUpdate()
        }
    }
    
    def promoteUser(id: BigInt): Integer = {
        Logger.debug(s"Promoting user: $id")
        DB.withConnection { implicit c =>
            return SQL"update users set admin = true where id = '$id'"
                .executeUpdate()
        }
    }
    
    def banUser(id: BigInt): Integer = {
        Logger.debug(s"Banning user: $id")
        DB.withConnection { implicit c =>
            return SQL"update users set banned = true where id = '$id'"
                .executeUpdate()
        }
    }
    
    // TODO 
    // There's quite a bit of repetitive code down there...
    // The entire receive method will be rewritten using the database stuff above.
    // The messages will need to be rewritten. 
        // For example, a join will be Join(id: BigInt). Users (front-end code) will know their bigint to join the room. It's the ID in chat.com/id.
        // For example, a New(name: String) message doesn't even exist yet, but it will be used to create a new room when a user doesn't have an id to join.
            // A New() message will create a room, put the user in that room, make that user the admin, and so on.
    // When users leave a room, check if there are still admins in the room. If there aren't any, promote the user who has the oldest join date.
    // That joinRoom method might be confusing when I see it tomorrow. Think of it this way. The user has no ID or no name until he joins a room.
        // When he joins the room, that method returns an ID for him. That's now his ID, and it's also his name until he renames himself.
    
    def receive = {
        case Join(room: String) =>
            Logger.debug(s"Received a join: $sender.path $room")
            registry get room match {
                case Some(users: Map[String, ActorRef]) =>
                    val name = "foo" //getUniqueId() TODO This whole section will be rewritten.
                    Logger.debug(s"Giving sender the name $name")
                    sender ! Name("", room, name)
                    Logger.debug(s"Adding $name to $room")
                    users += (name -> sender)
                    for ((username: String, ref: ActorRef) <- users) {
                        Logger.debug(s"Alerting users in $room of $name joining")
                        ref ! Message(registrar, room, userJoined(name))
                    }
                case None =>
                    val name = "foo" //getUniqueId() TODO This whole section will be rewritten.
                    Logger.debug(s"Giving sender the name $name")
                    sender ! Name("", room, name)
                    Logger.debug(s"Creating room $room")
                    registry += (room -> Map(name -> sender))
                    Logger.debug(s"Alerting users in $room of $name joining")
                    sender ! Message(registrar, room, userJoined(name))
            }
        case Leave(name: String, room: String) =>
            Logger.debug(s"Received a leave: $name, $room")
            registry get room match {
                case Some(users: Map[String, ActorRef]) =>
                    Logger.debug(s"Removing $name from $room")
                    users -= name
                    for ((username: String, ref: ActorRef) <- users) {
                        Logger.debug(s"Alerting users in $room of $name leaving")
                        ref ! Message(registrar, room, userLeft(name))
                    }
                case None =>
                    Logger.debug("Room $room doesn't exist")
            }
        case Disconnect(name: String) =>
            // TODO This is bad. I don't like looping through all rooms to find instances of the user.
            // I should have a better data structure.
            Logger.debug(s"Received a disconnect: $name")
            for ((room: String, users: Map[String, ActorRef]) <- registry) {
                if (users contains name)
                    Logger.debug(s"Forwarding leave alert to users in $room")
                    self forward Leave(name, room)
            }
        case Name(name: String, room: String, change: String) =>
            Logger.debug(s"Received a name change: $name, $change")
            registry get room match {
                case Some(users: Map[String, ActorRef]) =>
                    users get change match {
                        case Some(taken: ActorRef) =>
                            Logger.debug(s"Informing user $name that name $change is taken in $room")
                            sender ! Message(registrar, room, nameIsTaken(name))
                        case None =>
                            Logger.debug(s"Renaming $name to $change in $room")
                            users -= name
                            users += (change -> sender)
                            Logger.debug(s"Alerting users in $room of $name changing name to $change")
                            self ! Message(registrar, room, userChangedName(name, change))
                            Logger.debug(s"Sending approved name change to $name")
                            sender ! Name(name, room, change)
                    }
                case None =>
                    // Nobody is in this room...?
            }
        case Message(name: String, room: String, message: String) =>
            Logger.debug(s"Received a message: $name, $room, $message")
            registry get room match {
                case Some(users: Map[String, ActorRef]) =>
                    Logger.debug(s"Sending message to users in $room")
                    for ((username: String, ref: ActorRef) <- users) {
                        ref ! Message(name, room, message)
                    }
                case None =>
                    Logger.debug(s"$name is attempting to message $room which doesn't exist")
                    self forward Join(room)
            }
    }
}