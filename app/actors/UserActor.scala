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
        Logger info s"UserActor $self is starting up"
    }
    
    override def postStop() = {
        Logger info s"UserActor $self is shutting down"
    }

    object User {
        val parser: RowParser[User] = {
            str("USERS.USER_ID") ~
            str("USERS.ACTOR_NAME") ~
            str("USERS.ACTOR_PATH") ~
            str("USERS.ROOM_ID") ~
            str("USERS.USER_NAME") ~
            date("USERS.JOIN_DATE") ~
            bool("USERS.IS_ADMIN") map {
            case userId ~ actorName ~ actorPath ~ roomId ~ userName ~ joinDate ~ isAdmin =>
                messages.User(userId, actorName, actorPath, roomId, userName, joinDate, isAdmin)
            }
        }
    }

    def joinRoom(actorName: String, actorPath: String, roomId: String, userName: String): Option[String] = {
        Logger debug s"Joining room: $roomId"
        DB.withConnection { implicit c =>
            return SQL"""INSERT INTO USERS (ACTOR_NAME, ACTOR_PATH, ROOM_ID, USER_NAME)
                VALUES ($actorName, $actorPath, $roomId, $userName)
                """
                .executeInsert(str("USERS.USER_ID").singleOpt)
        }
    }

    def leaveRoom(actorName: String, roomId: String): Int = {
        Logger debug s"Leaving room: $actorName, $roomId"
        DB.withConnection { implicit c =>
            return SQL"DELETE FROM USERS WHERE ACTOR_NAME = $actorName AND ROOM_ID = $roomId"
                .executeUpdate()
        }
    }

    def getUserByActorName(actorName: String, roomId: String): Option[User] = {
        Logger debug s"Getting user: $actorName, $roomId"
        DB.withConnection { implicit c =>
            return SQL"""SELECT (USER_ID, ACTOR_NAME, ACTOR_PATH, ROOM_ID, USER_NAME, JOIN_DATE, IS_ADMIN)
                FROM USERS
                WHERE ACTOR_NAME = $actorName
                AND ROOM_ID = $roomId
                """
                .as(User.parser.singleOpt)
        }
    }

    def getUserByUserName(userName: String, roomId: String): Option[User] = {
        Logger debug s"Getting user: $userName, $roomId"
        DB withConnection { implicit c =>
            return SQL"""SELECT (USER_ID, ACTOR_NAME, ACTOR_PATH, ROOM_ID, USER_NAME, JOIN_DATE, IS_ADMIN)
                FROM USERS
                WHERE USER_NAME = $userName AND ROOM_ID = $roomId
                """
                .as(User.parser.singleOpt)
        }
    }

    def getUsersByRoomId(roomId: String): List[User] = {
        Logger debug s"Getting users in room: $roomId"
        DB.withConnection { implicit c =>
            return SQL"""SELECT (USER_ID, ACTOR_NAME, ACTOR_PATH, ROOM_ID, USER_NAME, JOIN_DATE, IS_ADMIN)
                FROM USERS
                WHERE ROOM_ID = $roomId
                """
                .as(User.parser.*)
        }
    }

    def getUsersByActorName(actorName: String): List[User] = {
        Logger debug s"Getting users with path: $actorName"
        DB.withConnection { implicit c =>
            return SQL"""SELECT (USER_ID, ACTOR_NAME, ACTOR_PATH, ROOM_ID, USER_NAME, JOIN_DATE, IS_ADMIN)
                FROM USERS
                WHERE ACTOR_NAME = $actorName
                """
                .as(User.parser.*)
        }
    }

    def getAllUsers: List[User] = {
        Logger debug s"Getting all users"
        DB.withConnection { implicit c =>
            return SQL"""SELECT (USER_ID, ACTOR_NAME, ACTOR_PATH, ROOM_ID, USER_NAME, JOIN_DATE, IS_ADMIN)
                FROM USERS
                """
                .as(User.parser.*)
        }
    }

    def nameUser(actorName: String, userName: String, roomId: String): Int = {
        Logger debug s"Renaming user: $actorName, $userName, $roomId"
        DB.withConnection { implicit c =>
            return SQL"""UPDATE USERS SET USER_NAME = $userName
                WHERE ACTOR_NAME = $actorName AND ROOM_ID = $roomId
                """
                .executeUpdate()
        }
    }

    def promoteUser(userName: String, roomId: String): Int = {
        Logger debug s"Promoting user: $userName, $roomId"
        DB.withConnection { implicit c =>
            return SQL"""UPDATE USERS SET IS_ADMIN = TRUE
                WHERE USER_NAME = $userName"
                AND ROOM_ID = $roomId
                """
                .executeUpdate()
        }
    }

    def findValidUserName(userName: String, roomId: String): String = {
        Logger debug s"Trying to find a valid userName starting with $userName"
        for (i <- 1 to 10) {
            val attempt = i match {
                case 1 => userName
                case _ => userName + (System.currentTimeMillis / 1000).toString
            }
            getUserByUserName(attempt, roomId) match {
                case Some(user: User) =>
                case None => return attempt
            }
        }
        userName
        // TODO At the point where I return the original userName, I know that it will fail.
        // So I'll just tell the user "Failed to join room with userName: $userName."
        // He can try again to see if it was just a fluke, or maybe there was a real problem somewhere?
        // Is there a better way for me to handle the case where I can't find a valid userName?
    }

    // TODO Users and Rooms tables have limits on columns now. Before doing anything, check if the value
    // is too long. If it is, trim it before doing the thing.
    
    def receive = {
        case JoinRoom(roomId: String, userName: String) =>
            Logger debug s"Received a JoinRoom: $roomId, $userName"
            val actorName = sender().path.name
            val actorPath = sender().path.toSerializationFormat
            val ref = sender()
            registrar ? GetRoom(roomId) onSuccess {
                case Some(room: Room) =>
                    val validName = findValidUserName(userName, roomId)
                    joinRoom(actorName, actorPath, roomId, validName) match {
                        case Some(userId: String) =>
                            registrar ! SystemMessage(roomId, s"User $validName has joined the room")
                            ref ! NameUser(validName, roomId)
                        case None => ref ! GlobalSystemMessage(s"Failed to join room $roomId with name $validName")
                    }
                case None => ref ! GlobalSystemMessage(s"Failed to join room: $roomId")
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
        case GetAllUsers() =>
            Logger debug s"Received a GetAllUsers"
            sender ! getAllUsers
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
        case DisconnectUser(actorName: String) =>
            Logger debug s"Received a DisconnectUser: $actorName"
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