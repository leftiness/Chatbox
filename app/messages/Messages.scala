package messages

import java.util.Date

import akka.actor._

case class OpenSocket(ref: ActorRef)
case class CloseSocket(ref: ActorRef)

case class JoinRoom(roomId: String)
case class LeaveRoom(roomId: String)
case class GetUser(actorName: String, roomId: String)
case class GetUsers(roomId: String)
case class NameUser(userName: String, roomId: String)
case class PromoteUser(userName: String, roomId: String)
case class BanUser(userName: String, roomId: String)
case class DisconnectUser(actorName: String)

case class NewRoom(roomName: String)
case class GetRoom(roomId: String)
case class NameRoom(roomId: String, roomName: String)
case class DeleteRoom(roomId: String)

case class GlobalSystemMessage(messageText: String)
case class SystemMessage(roomId: String, messageText: String)
case class MessageIn(roomId: String, messageText: String)
case class MessageOut(userName: String, roomId: String, messageText: String)

case class User(userId: String, actorName: String, actorPath: String, roomId: String, userName: String, joinDate: Date, isAdmin: Boolean)
case class Room(roomId: String, roomName: String)