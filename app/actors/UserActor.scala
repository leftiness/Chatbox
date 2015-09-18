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

class UserActor() extends Actor {
    val registrar = context.parent

    implicit val timeout = Timeout(5 seconds)
    
    override def preStart() = {
        Logger info s"UserActor $self.path is starting up"
    }
    
    override def postStop() = {
        Logger info s"UserActor $self.path is shutting down"
    }
    
    object User {
        val parser: RowParser[User] = {
            get[BigInt]("users.id") ~
            get[BigInt]("users.room") ~
            str("users.name") ~
            date("users.joined") ~
            bool("users.admin") ~
            bool("users.banned") ~
            str("users.path") map {
                case user ~ room ~ name ~ joined ~ admin ~ banned ~ path =>
                    messages.User(user, room, name, joined, admin, banned, path)
            }
        }
    }
    
    def joinRoom(roomId: BigInt, path: String): Option[BigInt] = {
        Logger debug s"Joining room: $roomId"
        DB.withConnection { implicit c =>
            return SQL"insert into users (room, path) values ('$roomId', '$path')"
                .executeInsert(get[BigInt]("id").singleOpt)
        }
    }
    
    def leaveRoom(userId: BigInt, roomId: BigInt): Integer = {
        Logger debug s"Leaving room: $userId, $roomId"
        DB.withConnection { implicit c =>
            return SQL"delete from users where id = '$userId' and room = '$roomId'"
                .executeUpdate()
        }
    }
    
    def getUserById(userId: BigInt): Option[User] = {
        Logger debug s"Getting user: $userId"
        DB.withConnection { implicit c =>
            return SQL"""select (id, room, name, joined, admin, banned) 
                from users
                where id = '$userId'
                """
                .as(User.parser.singleOpt)
        }
    }
    
    def getUserByName(userName: String, roomId: BigInt): Option[User] = {
        Logger debug s"Getting user: $userName"
        DB withConnection { implicit c =>
            return SQL"""select (id, room, name, joined, admin, banned)
                from users
                where name = '$userName' and room = '$roomId'"""
                .as(User.parser.singleOpt)
        }
    }
    
    def getUsers(roomId: BigInt): List[User] = {
        Logger debug s"Getting users in room: $roomId"
        DB.withConnection { implicit c =>
            return SQL"""select (id, room, name, joined, admin, banned)
                from users
                where room = '$roomId'
                """
                .as(User.parser.*)
        }
    }
    
    def nameUser(userId: BigInt, userName: String, roomId: BigInt): Integer = {
        Logger debug s"Renaming user: $userId, $userName"
        DB.withConnection { implicit c =>
            return SQL"""update users set name = '$userName' 
                where id = '$userId' and name = '$userName'"""
                .executeUpdate()
        }
    }
    
    def promoteUser(userId: BigInt): Integer = {
        Logger debug s"Promoting user: $userId"
        DB.withConnection { implicit c =>
            return SQL"update users set admin = true where id = '$userId'"
                .executeUpdate()
        }
    }
    
    def banUser(userId: BigInt): Integer = {
        Logger debug s"Banning user: $userId"
        DB.withConnection { implicit c =>
            return SQL"update users set banned = true where id = '$userId'"
                .executeUpdate()
        }
    }
    
    def receive = {
        case JoinRoom(roomId: BigInt, actorPath: String) =>
            Logger debug s"Received a JoinRoom: $roomId"
            registrar ? GetRoom(roomId) onSuccess {
                case Some(room: Room) =>
                    sender ! joinRoom(roomId, actorPath)
            }
        case LeaveRoom(userId: BigInt, roomId: BigInt) =>
            Logger debug s"Received a LeaveRoom: $userId, $roomId"
            sender ! leaveRoom(userId, roomId)
        case GetUser(userId: BigInt) =>
            Logger debug s"Received a GetUser: $userId"
            sender ! getUserById(userId)
        case GetUsers(roomId: BigInt) =>
            Logger debug s"Received a GetUsers: $roomId"
            sender ! getUsers(roomId)
        case NameUser(userId: BigInt, userName: String, roomId: BigInt) =>
            Logger debug s"Received a NameUser: $userId, $userName, $roomId"
            getUserByName(userName, roomId) match {
                case Some(user) => // TODO The user exists. Fail this request.
                case None => sender ! nameUser(userId, userName, roomId)
            }
        case PromoteUser(userId: BigInt) =>
            Logger debug s"Received a PromoteUser: $userId"
            sender ! promoteUser(userId)
        case BanUser(userId: BigInt) =>
            Logger debug s"Received a BanUser: $userId"
            sender ! banUser(userId)
    }

}