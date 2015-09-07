package messages

import akka.actor._

case class Join(name: String, room: String)
case class Leave(name: String, room: String)
case class Disconnect(name: String)
case class Message(name: String, room: String, message: String)
case class NameChange(name: String, change: String)