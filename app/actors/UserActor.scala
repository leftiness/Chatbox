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

    implicit val timeout = Timeout(5.seconds)
    
    override def preStart() = {
        Logger info s"UserActor $self.path is starting up"
    }
    
    override def postStop() = {
        Logger info s"UserActor $self.path is shutting down"
    }
    
    object User {
        val parser: RowParser[User] = {
            str("users.actorPath") ~
            str("users.roomId") ~
            str("users.userName") ~
            date("users.joinDate") ~
            bool("users.isAdmin") ~
            bool("users.isBanned") map {
                case actorPath ~ roomId ~ userName ~ joinDate ~ isAdmin ~ isBanned =>
                    messages.User(actorPath, roomId, userName, joinDate, isAdmin, isBanned)
            }
        }
    }
    
    def joinRoom(actorPath: String, roomId: String): Option[String] = {
        Logger debug s"Joining room: $roomId"
        DB.withConnection { implicit c =>
            return SQL"insert into users (actorPath, roomId) values ('$actorPath', '$roomId')"
                .executeInsert(str("users.actorPath").singleOpt)
        }
    }
    
    def leaveRoom(actorPath: String, roomId: String): Integer = {
        Logger debug s"Leaving room: $actorPath, $roomId"
        DB.withConnection { implicit c =>
            return SQL"delete from users where actorPath = '$actorPath' and roomId = '$roomId'"
                .executeUpdate()
        }
    }

    def getUserByPath(actorPath: String, roomId: String): Option[User] = {
        Logger debug s"Getting user: $actorPath, $roomId"
        DB.withConnection { implicit c =>
            return SQL"""select (actorPath, roomId, userName, joinDate, isAdmin, isBanned)
                from users
                where actorPath = '$actorPath'
                and roomId = '$roomId'
                """
                .as(User.parser.singleOpt)
        }
    }
    
    def getUserByName(userName: String, roomId: String): Option[User] = {
        Logger debug s"Getting user: $userName, $roomId"
        DB withConnection { implicit c =>
            return SQL"""select (actorPath, roomId, userName, joinDate, isAdmin, isBanned)
                from users
                where userName = '$userName' and roomId = '$roomId'
                """
                .as(User.parser.singleOpt)
        }
    }
    
    def getUsers(roomId: String): List[User] = {
        Logger debug s"Getting users in room: $roomId"
        DB.withConnection { implicit c =>
            return SQL"""select (actorPath, roomId, userName, joinDate, isAdmin, isBanned)
                from users
                where roomId = '$roomId'
                """
                .as(User.parser.*)
        }
    }

    def getUsersByPath(actorPath: String): List[User] = {
        Logger debug s"Getting users with path: $actorPath"
        DB.withConnection { implicit c =>
            return SQL"""select (actorPath, roomId, userName, joinDate, isAdmin, isBanned)
                from users
                where actorPath = '$actorPath'
                """
                .as(User.parser.*)
        }
    }
    
    def nameUser(actorPath: String, userName: String, roomId: String): Integer = {
        Logger debug s"Renaming user: $actorPath, $userName, $roomId"
        DB.withConnection { implicit c =>
            return SQL"""update users set userName = '$userName'
                where actorPath = '$actorPath' and roomId = '$roomId'
                """
                .executeUpdate()
        }
    }
    
    def promoteUser(userName: String, roomId: String): Integer = {
        Logger debug s"Promoting user: $userName, $roomId"
        DB.withConnection { implicit c =>
            return SQL"""update users set isAdmin = true
                where userName = '$userName'"
                and roomId = '$roomId'
                """
                .executeUpdate()
        }
    }
    
    def banUser(userName: String, roomId: String): Integer = {
        Logger debug s"Banning user: $userName, $roomId"
        DB.withConnection { implicit c =>
            return SQL"""update users set isBanned = true
                where userName = '$userName'
                and roomId = '$roomId'
                """
                .executeUpdate()
        }
    }
    
    def receive = {
        case JoinRoom(roomId: String) =>
            Logger debug s"Received a JoinRoom: $roomId"
            val path = sender().path.toSerializationFormat
            registrar ? GetRoom(roomId) onSuccess {
                case Some(room: Room) =>
                    joinRoom(roomId, path) match {
                        case Some(_: String) => // TODO registrar forward UserJoined(stuff that you need to send to each user to show a message and update the UI)
                        case None => sender ! GlobalSystemMessage(s"Failed to join room: $roomId")
                    }
                case None => sender ! GlobalSystemMessage(s"Failed to join room: $roomId")

            }
        case LeaveRoom(roomId: String) =>
            Logger debug s"Received a LeaveRoom: $roomId"
            val path = sender().path.toSerializationFormat
            val result = leaveRoom(path, roomId)
            if (result == 0)
                // TODO More lame if statements...
                sender ! GlobalSystemMessage(s"Failed to leave room: $roomId")
            else
                println("todo")
                // TODO registrar forward UserLeft(stuff for every user in room)

        case GetUser(actorPath: String, roomId: String) =>
            Logger debug s"Received a GetUser: $actorPath, $roomId"
            sender ! getUserByPath(actorPath, roomId)
        case GetUsers(roomId: String) =>
            Logger debug s"Received a GetUsers: $roomId"
            sender ! getUsers(roomId)
        case NameUser(userName: String, roomId: String) =>
            Logger debug s"Received a NameUser: $userName, $roomId"
            val path = sender().path.toSerializationFormat
            getUserByName(userName, roomId) match {
                case Some(_: User) => sender ! SystemMessage(roomId, s"Name $userName is already taken")
                case None =>
                    val result = nameUser(path, userName, roomId)
                    if (result == 0)
                        // TODO More lame if statements...
                        sender ! SystemMessage(roomId, s"Failed to take name $userName")
                    else
                        println("todo")
                        // TODO registrar forward RenamedUser(stuff for every user in room)
            }
        case PromoteUser(userName: String, roomId: String) =>
            Logger debug s"Received a PromoteUser: $userName"
            val path = sender().path.toSerializationFormat
            getUserByPath(path, roomId) match {
                case Some(user: User) =>
                    if (user.isAdmin) {
                        // TODO If statements feel wrong... isn't there something cool scala can do here?
                        val result = promoteUser(userName, roomId)
                        if (result == 0) {
                            sender ! SystemMessage(roomId, s"Failed to promote user $userName")
                        } else {
                            // TODO registrar forward PromotedUser(stuff for every user in room)
                        }
                    } else {
                        sender ! SystemMessage(roomId, s"Failed to promote user $userName")
                    }
                case None => sender ! SystemMessage(roomId, s"Failed to promote user $userName")
            }
        case BanUser(userName: String, roomId: String) =>
            Logger debug s"Received a BanUser: $userName"
            val path = sender().path.toSerializationFormat
            getUserByPath(path, roomId) match {
                case Some(user: User) =>
                    if (user.isAdmin) {
                        // TODO If statements feel wrong... isn't there something cool scala can do here?
                        val result = banUser(userName, roomId)
                        if (result == 0) {
                            sender ! SystemMessage(roomId, s"Failed to ban user $userName")
                        } else {
                            // TODO registrar forward BannedUser(stuff for every user in room)
                        }
                    } else {
                        sender ! SystemMessage(roomId, s"Failed to ban user $userName")
                    }
                case None => sender ! SystemMessage(roomId, s"Failed to ban user $userName")
            }
        case DisconnectUser(actorPath: String) =>
            Logger debug s"Received a DeleteUser: $actorPath"
            getUsersByPath(actorPath) match {
                case users: List[User] =>
                    users.map(u => (u.userName, u.roomId)) foreach { user =>
                        val userName = user._1
                        val roomId = user._2
                        // TODO registrar ! UserLeft(userName, roomId, other stuff for all users to update UI???)
                        leaveRoom(actorPath, roomId)
                    }
            }
    }

}