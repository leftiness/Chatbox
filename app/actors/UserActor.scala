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
            str("users.actorName") ~
            str("users.roomId") ~
            str("users.userName") ~
            date("users.joinDate") ~
            bool("users.isAdmin") ~
            bool("users.isBanned") map {
                case actorName ~ roomId ~ userName ~ joinDate ~ isAdmin ~ isBanned =>
                    messages.User(actorName, roomId, userName, joinDate, isAdmin, isBanned)
            }
        }
    }
    
    def joinRoom(actorName: String, roomId: String): Option[String] = {
        Logger debug s"Joining room: $roomId"
        DB.withConnection { implicit c =>
            return SQL"insert into users (actorName, roomId) values ('$actorName', '$roomId')"
                .executeInsert(str("users.actorName").singleOpt)
        }
    }
    
    def leaveRoom(actorName: String, roomId: String): Int = {
        Logger debug s"Leaving room: $actorName, $roomId"
        DB.withConnection { implicit c =>
            return SQL"delete from users where actorName = '$actorName' and roomId = '$roomId'"
                .executeUpdate()
        }
    }

    def getUserByActorName(actorName: String, roomId: String): Option[User] = {
        Logger debug s"Getting user: $actorName, $roomId"
        DB.withConnection { implicit c =>
            return SQL"""select (actorName, roomId, userName, joinDate, isAdmin, isBanned)
                from users
                where actorName = '$actorName'
                and roomId = '$roomId'
                """
                .as(User.parser.singleOpt)
        }
    }
    
    def getUserByUserName(userName: String, roomId: String): Option[User] = {
        Logger debug s"Getting user: $userName, $roomId"
        DB withConnection { implicit c =>
            return SQL"""select (actorName, roomId, userName, joinDate, isAdmin, isBanned)
                from users
                where userName = '$userName' and roomId = '$roomId'
                """
                .as(User.parser.singleOpt)
        }
    }
    
    def getUsersByRoomId(roomId: String): List[User] = {
        Logger debug s"Getting users in room: $roomId"
        DB.withConnection { implicit c =>
            return SQL"""select (actorName, roomId, userName, joinDate, isAdmin, isBanned)
                from users
                where roomId = '$roomId'
                """
                .as(User.parser.*)
        }
    }

    def getUsersByActorName(actorName: String): List[User] = {
        Logger debug s"Getting users with path: $actorName"
        DB.withConnection { implicit c =>
            return SQL"""select (actorName, roomId, userName, joinDate, isAdmin, isBanned)
                from users
                where actorName = '$actorName'
                """
                .as(User.parser.*)
        }
    }
    
    def nameUser(actorName: String, userName: String, roomId: String): Int = {
        Logger debug s"Renaming user: $actorName, $userName, $roomId"
        DB.withConnection { implicit c =>
            return SQL"""update users set userName = '$userName'
                where actorName = '$actorName' and roomId = '$roomId'
                """
                .executeUpdate()
        }
    }
    
    def promoteUser(userName: String, roomId: String): Int = {
        Logger debug s"Promoting user: $userName, $roomId"
        DB.withConnection { implicit c =>
            return SQL"""update users set isAdmin = true
                where userName = '$userName'"
                and roomId = '$roomId'
                """
                .executeUpdate()
        }
    }
    
    def banUser(userName: String, roomId: String): Int = {
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
            val actorName = sender().path.name
            registrar ? GetRoom(roomId) onSuccess {
                case Some(room: Room) =>
                    joinRoom(roomId, actorName) match {
                        case Some(_: String) => registrar ! SystemMessage(roomId, s"User $actorName has joined the room")
                        case None => sender ! GlobalSystemMessage(s"Failed to join room: $roomId")
                    }
                case None => sender ! GlobalSystemMessage(s"Failed to join room: $roomId")

            }
        case LeaveRoom(roomId: String) =>
            Logger debug s"Received a LeaveRoom: $roomId"
            val actorName = sender().path.name
            getUserByActorName(actorName, roomId) match {
                case Some(user: User) => leaveRoom(actorName, roomId) match {
                    case 0 => sender ! GlobalSystemMessage(s"Failed to leave room: $roomId")
                    case _ =>
                        val userName = user.userName
                        registrar ! SystemMessage(roomId, s"User $userName has left the room")
                }
                case None => // TODO This user doesn't exist?
            }
        case GetUser(actorName: String, roomId: String) =>
            Logger debug s"Received a GetUser: $actorName, $roomId"
            sender ! getUserByActorName(actorName, roomId)
        case GetUsers(roomId: String) =>
            Logger debug s"Received a GetUsers: $roomId"
            sender ! getUsersByRoomId(roomId)
        case NameUser(userName: String, roomId: String) =>
            Logger debug s"Received a NameUser: $userName, $roomId"
            val actorName = sender().path.name
            getUserByActorName(actorName, roomId) match {
                case Some(user: User) => getUserByUserName(userName, roomId) match {
                    case Some(_: User) => sender ! SystemMessage(roomId, s"Name $userName is already taken")
                    case None => nameUser(actorName, userName, roomId) match {
                        case 0 => sender ! SystemMessage(roomId, s"Failed to take name $userName")
                        case _ =>
                            val oldUserName = user.userName
                            registrar ! SystemMessage(roomId, s"User $oldUserName is now known as $userName")
                    }
                }
                case None => // TODO This user doesn't exist?
            }
        case PromoteUser(userName: String, roomId: String) =>
            Logger debug s"Received a PromoteUser: $userName"
            val actorName = sender().path.name
            getUserByActorName(actorName, roomId) match {
                case Some(user: User) => user.isAdmin match {
                    case true => promoteUser(userName, roomId) match {
                        case 0 => sender ! SystemMessage(roomId, s"Failed to promote user $userName")
                        case _ => registrar ! SystemMessage(roomId, s"User $userName is now an admin")
                    }
                    case false => sender ! SystemMessage(roomId, "You must be an admin to promote someone")
                }
                case None => sender ! SystemMessage(roomId, s"User $userName does not exist")
            }
        case BanUser(userName: String, roomId: String) =>
            Logger debug s"Received a BanUser: $userName"
            val actorName = sender().path.name
            getUserByActorName(actorName, roomId) match {
                case Some(user: User) => user.isAdmin match {
                    case true => banUser(userName, roomId) match {
                        case 0 => sender ! SystemMessage(roomId, s"Failed to ban user $userName")
                        case _ => registrar ! SystemMessage(roomId, s"User $userName is now banned")
                    }
                    case false => sender ! SystemMessage(roomId, "You must be an admin to ban someone")
                }
                case None => sender ! SystemMessage(roomId, s"User $userName does not exist")
            }
        case DisconnectUser(actorName: String) =>
            Logger debug s"Received a DeleteUser: $actorName"
            getUsersByActorName(actorName) match {
                case users: List[User] =>
                    users.map(u => (u.userName, u.roomId)) foreach { user =>
                        val userName = user._1
                        val roomId = user._2
                        registrar ! SystemMessage(roomId, s"User $userName has left the room")
                        leaveRoom(actorName, roomId)
                    }
            }
    }

}