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
    val maxUserNameLength = 255

    implicit val timeout = Timeout(5.seconds)
    
    override def preStart() = {
        Logger info s"UserActor $self is starting up"
    }
    
    override def postStop() = {
        Logger info s"UserActor $self is shutting down"
    }

    object User {
        val parser: RowParser[User] = {
            long("USERS.USER_ID") ~
            str("USERS.ACTOR_NAME") ~
            str("USERS.ACTOR_PATH") ~
            long("USERS.ROOM_ID") ~
            str("USERS.USER_NAME") ~
            date("USERS.JOIN_DATE") ~
            bool("USERS.IS_ADMIN") map {
            case userId ~ actorName ~ actorPath ~ roomId ~ userName ~ joinDate ~ isAdmin =>
                messages.User(userId, actorName, actorPath, roomId, userName, joinDate, isAdmin)
            }
        }
    }

    def joinRoom(actorName: String, actorPath: String, roomId: Long, userName: String): Option[Long] = {
        Logger debug s"Joining room: $roomId"
        DB.withConnection { implicit c =>
            return SQL"""INSERT INTO USERS (ACTOR_NAME, ACTOR_PATH, ROOM_ID, USER_NAME)
                VALUES ($actorName, $actorPath, $roomId, $userName)
                """
                .executeInsert()
        }
    }

    def leaveRoom(actorName: String, roomId: Long): Int = {
        Logger debug s"Leaving room: $actorName, $roomId"
        DB.withConnection { implicit c =>
            return SQL"DELETE FROM USERS WHERE ACTOR_NAME = $actorName AND ROOM_ID = $roomId"
                .executeUpdate()
        }
    }

    def getUserByActorName(actorName: String, roomId: Long): Option[User] = {
        Logger debug s"Getting user: $actorName, $roomId"
        DB.withConnection { implicit c =>
            return SQL"""SELECT USER_ID, ACTOR_NAME, ACTOR_PATH, ROOM_ID, USER_NAME, JOIN_DATE, IS_ADMIN
                FROM USERS
                WHERE ACTOR_NAME = $actorName
                AND ROOM_ID = $roomId
                """
                .as(User.parser.singleOpt)
        }
    }

    def getUserByUserName(userName: String, roomId: Long): Option[User] = {
        Logger debug s"Getting user: $userName, $roomId"
        DB withConnection { implicit c =>
            return SQL"""SELECT USER_ID, ACTOR_NAME, ACTOR_PATH, ROOM_ID, USER_NAME, JOIN_DATE, IS_ADMIN
                FROM USERS
                WHERE USER_NAME = $userName AND ROOM_ID = $roomId
                """
                .as(User.parser.singleOpt)
        }
    }

    def getUsersByRoomId(roomId: Long): List[User] = {
        Logger debug s"Getting users in room: $roomId"
        DB.withConnection { implicit c =>
            return SQL"""SELECT USER_ID, ACTOR_NAME, ACTOR_PATH, ROOM_ID, USER_NAME, JOIN_DATE, IS_ADMIN
                FROM USERS
                WHERE ROOM_ID = $roomId
                """
                .as(User.parser.*)
        }
    }

    def getUsersByActorName(actorName: String): List[User] = {
        Logger debug s"Getting users with path: $actorName"
        DB.withConnection { implicit c =>
            return SQL"""SELECT USER_ID, ACTOR_NAME, ACTOR_PATH, ROOM_ID, USER_NAME, JOIN_DATE, IS_ADMIN
                FROM USERS
                WHERE ACTOR_NAME = $actorName
                """
                .as(User.parser.*)
        }
    }

    def getAllUsers: List[User] = {
        Logger debug s"Getting all users"
        DB.withConnection { implicit c =>
            return SQL"""SELECT USER_ID, ACTOR_NAME, ACTOR_PATH, ROOM_ID, USER_NAME, JOIN_DATE, IS_ADMIN
                FROM USERS
                """
                .as(User.parser.*)
        }
    }

    def nameUser(actorName: String, userName: String, roomId: Long): Int = {
        Logger debug s"Renaming user: $actorName, $userName, $roomId"
        DB.withConnection { implicit c =>
            return SQL"""UPDATE USERS SET USER_NAME = $userName
                WHERE ACTOR_NAME = $actorName AND ROOM_ID = $roomId
                """
                .executeUpdate()
        }
    }

    def promoteUser(userName: String, roomId: Long): Int = {
        Logger debug s"Promoting user: $userName, $roomId"
        DB.withConnection { implicit c =>
            return SQL"""UPDATE USERS SET IS_ADMIN = TRUE
                WHERE USER_NAME = $userName"
                AND ROOM_ID = $roomId
                """
                .executeUpdate()
        }
    }
    
    def receive = {
        case JoinRoom(roomId: Long, userName: String) =>
            Logger debug s"Received a JoinRoom: $roomId, $userName"
            val actorName = sender().path.name
            val actorPath = sender().path.toSerializationFormat
            val ref = sender()
            registrar ? GetRoom(roomId) onSuccess {
                case Some(room: Room) =>
                    val validName = getUserByUserName(userName, roomId) match {
                        case Some(user: User) =>
                            val uuid = java.util.UUID.randomUUID.toString
                            val name = userName.length match {
                                case length if length >= maxUserNameLength =>
                                    userName.substring(0, maxUserNameLength - uuid.length)
                                case _ => userName
                            }
                            name + uuid
                        case None => userName.length match {
                            case length if length >= maxUserNameLength => userName.substring(0, maxUserNameLength)
                            case _ => userName
                        }
                    }
                    joinRoom(actorName, actorPath, roomId, validName) match {
                        case Some(userId: Long) =>
                            registrar ! SystemMessage(roomId, s"User $validName has joined the room")
                            ref ! NameUser(validName, roomId)
                        case None => ref ! GlobalSystemMessage(s"Failed to join room $roomId with name $validName")
                    }
                case None => ref ! GlobalSystemMessage(s"Failed to join room: $roomId")
            }
        case LeaveRoom(roomId: Long) =>
            Logger debug s"Received a LeaveRoom: $roomId"
            val actorName = sender().path.name
            getUserByActorName(actorName, roomId) match {
                case Some(user: User) => leaveRoom(actorName, roomId) match {
                    case 0 => sender ! GlobalSystemMessage(s"Failed to leave room: $roomId")
                    case _ =>
                        val userName = user.userName
                        registrar ! SystemMessage(roomId, s"User $userName has left the room")
                }
                case None => sender ! GlobalSystemMessage(s"You are not a member of room $roomId")
            }
        case GetUser(actorName: String, roomId: Long) =>
            Logger debug s"Received a GetUser: $actorName, $roomId"
            sender ! getUserByActorName(actorName, roomId)
        case GetUsers(roomId: Long) =>
            Logger debug s"Received a GetUsers: $roomId"
            sender ! getUsersByRoomId(roomId)
        case GetAllUsers() =>
            Logger debug s"Received a GetAllUsers"
            sender ! getAllUsers
        case NameUser(userName: String, roomId: Long) =>
            Logger debug s"Received a NameUser: $userName, $roomId"
            val actorName = sender().path.name
            val validName = userName.length match {
                case _ if userName.length >= maxUserNameLength => userName.substring(0, maxUserNameLength)
                case _ => userName
            }
            getUserByActorName(actorName, roomId) match {
                case Some(user: User) => getUserByUserName(validName, roomId) match {
                    case Some(_: User) => sender ! SystemMessage(roomId, s"Name $validName is already taken")
                    case None => nameUser(actorName, validName, roomId) match {
                        case 0 => sender ! SystemMessage(roomId, s"Failed to take name $validName")
                        case _ =>
                            val oldUserName = user.userName
                            registrar ! SystemMessage(roomId, s"User $oldUserName is now known as $validName")
                    }
                }
                case None => sender ! GlobalSystemMessage(s"You are not a member of room $roomId")
            }
        case PromoteUser(userName: String, roomId: Long) =>
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