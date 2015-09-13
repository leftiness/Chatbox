package messages

import java.util.Date

import akka.actor._

case class OpenSocket(ref: ActorRef)

case class JoinRoom(userId: BigInt)
case class LeaveRoom(userId: BigInt, roomId: BigInt)
case class GetUser(userId: BigInt)
case class GetUsers(roomId: BigInt)
case class NameUser(userId: BigInt, userName: String, roomId: BigInt)
case class PromoteUser(userId: BigInt)
case class BanUser(userId: BigInt)

case class NewRoom(roomName: String)
case class GetRoom(roomId: BigInt)
case class NameRoom(roomId: BigInt, roomName: String)
case class DeleteRoom(roomId: BigInt)

case class Message(userId: BigInt, userName: String, roomId: BigInt, messageText: String)
case class User(userId: BigInt, roomId: BigInt, userName: String, joinDate: Date, isAdmin: Boolean, isBanned: Boolean, actorPath: String)
case class Room(roomId: BigInt, roomName: String)