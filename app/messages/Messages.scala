package messages

import java.util.Date

import akka.actor._

case class OpenSocket(ref: ActorRef)
case class CloseSocket(ref: ActorRef)

case class JoinRoom(roomId: Long, userName: String)
case class LeaveRoom(roomId: Long)
case class GetUser(actorName: String, roomId: Long)
case class GetUsers(roomId: Long)
case class GetAllUsers()
case class NameUser(userName: String, roomId: Long)
case class PromoteUser(userName: String, roomId: Long)
case class DisconnectUser(actorName: String)

case class NewRoom(roomName: String, userName: String)
case class GetRoom(roomId: Long)
case class NameRoom(roomId: Long, roomName: String)
case class DeleteRoom(roomId: Long)

case class GlobalSystemMessage(messageText: String)
case class SystemMessage(roomId: Long, messageText: String)
case class MessageIn(roomId: Long, messageText: String)
case class MessageOut(userName: String, roomId: Long, messageText: String)

case class User(userId: Long, actorName: String, actorPath: String, roomId: Long, userName: String, joinDate: Date, isAdmin: Boolean)
case class Room(roomId: Long, roomName: String)