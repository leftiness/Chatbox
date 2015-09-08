package messages

import akka.actor._

case class Join(room: String)
case class Leave(name: String, room: String)
case class Disconnect(name: String)
case class Message(name: String, room: String, message: String)
case class Name(name: String, room: String, change: String)