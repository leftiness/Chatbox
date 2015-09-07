package actors

import akka.actor._
import messages._
import scala.collection.mutable._

class CourierActor extends Actor {
    val chat = new HashMap[String, Map[String, ActorRef]]()
    val system = "System"
    
    def userJoined(name: String): String = {
        return "$name has joined the room."
    }
    
    def userLeft(name: String): String = {
        return "$name has left the room."
    }
    
    // TODO 
    // Remove join/leave/disconnect from the courier. A RegistrarActor should handle that.
    // The registrar actor will keep track of the users and which rooms they're in.
    // When the courier gets a join, he'll send it on to the registrar. Then the registrar will tell the courier to tell the users that someone joined. He'll tell the courier which users those are.
    // When the courier gets a message, he ask the registrar for refs to users who are in the room. Then he'll deliver those messages.
    // The registrar will also handle determining what automatically generated name new users should receive since he knows what names already exist.
    
    def receive = {
        case Join(name: String, room: String) =>
            chat.get(room) match {
                case Some(users: Map[String, ActorRef]) =>
                    users += (name -> sender)
                    self ! Message(system, room, userJoined(name))
                case None =>
                    chat += (room -> Map(name -> sender))
                    self ! Message(system, room, userJoined(name))
            }
        case Leave(name: String, room: String) =>
            chat.get(room) match {
                case Some(users: Map[String, ActorRef]) =>
                    users -= name
                    self ! Message(system, room, userLeft(name))
                case None =>
                    // TODO user ! That room never existed...
            }
        case Disconnect(name: String) =>
            chat.foreach { room: (String, Map[String, ActorRef]) =>
                self ! Leave(name, room._1)
            }
        case Message(name: String, room: String, message: String) =>
            chat.get(room) match {
                case Some(users: Map[String, ActorRef]) =>
                    users.foreach { user: (String, ActorRef) =>
                        user._2 ! Message(name, room, message)
                    }
                case None =>
                    self ! Join(name, room)
                    sender ! Message(name, room, message)
            }
    }
}