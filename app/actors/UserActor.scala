package actors

import akka.actor._
import anorm._
import anorm.SqlParser._
import play.api.db._
import play.api.Logger
import play.api.Play.current

import messages._

class UserActor() extends Actor {
    val registrar = context.parent
    
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
            bool("users.banned") map {
                case user ~ room ~ name ~ joined ~ admin ~ banned =>
                    messages.User(user, room, name, joined, admin, banned)
            }
        }
    }
    
    def joinRoom(roomId: BigInt): Option[BigInt] = {
        Logger debug s"Joining room: $roomId"
        DB.withConnection { implicit c =>
            return SQL"insert into users (room) values ('$roomId')"
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
        case JoinRoom(roomId: BigInt) =>
            Logger debug s"Received a JoinRoom: $roomId"
            sender ! joinRoom(roomId)
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