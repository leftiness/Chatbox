package actors

import akka.actor._
import messages._
import play.api.Logger
import scala.collection.mutable._
import scala.concurrent._
import ExecutionContext.Implicits.global

class RegistrarActor extends Actor {
    val registry = new HashMap[String, Map[String, ActorRef]]()
    val registrar = "Registrar"
    
    override def preStart() = {
        Logger.info(s"RegistrarActor $self.path is starting up")
    }
    
    override def postStop() = {
        Logger.info(s"RegistrarActor $self.path is shutting down")
    }
    
    def userJoined(name: String): String = {
        return s"$name has joined the room"
    }
    
    def userChangedName(name: String, change: String): String = {
        return s"$name is now known as $change"
    }
    
    def nameIsTaken(name: String): String = {
        return s"The name $name is already taken"
    }
    
    def getUniqueId(): String = {
        // TODO This is not a proper unique id...
        return (System.currentTimeMillis / 1000).toString()
    }
    
    def receive = {
        case Join(room: String) =>
            Logger.debug(s"Received a join: $sender.path $room")
            registry get room match {
                case Some(users: Map[String, ActorRef]) =>
                    val name = getUniqueId()
                    Logger.debug(s"Giving sender the name $name")
                    sender ! Name("", room, name)
                    Logger.debug(s"Adding $name to $room")
                    users += (name -> sender)
                    for ((username: String, ref: ActorRef) <- users) {
                        Logger.debug(s"Alerting users in $room of $name joining")
                        ref ! Message(registrar, room, userJoined(name))
                    }
                case None =>
                    val name = getUniqueId()
                    Logger.debug(s"Giving sender the name $name")
                    sender ! Name("", room, name)
                    Logger.debug(s"Creating room $room")
                    registry += (room -> Map(name -> sender))
                    Logger.debug(s"Alerting users in $room of $name joining")
                    sender ! Message(registrar, room, userJoined(name))
            }
        case Name(name: String, room: String, change: String) =>
            Logger.debug(s"Received a name change: $name, $change")
            registry get room match {
                case Some(users: Map[String, ActorRef]) =>
                    users get change match {
                        case Some(taken: ActorRef) =>
                            Logger.debug(s"Informing user $name that name $change is taken in $room")
                            sender ! Message(registrar, room, nameIsTaken(name))
                        case None =>
                            Logger.debug(s"Renaming $name to $change in $room")
                            users -= name
                            users += (change -> sender)
                            Logger.debug(s"Alerting users in $room of $name changing name to $change")
                            self ! Message(registrar, room, userChangedName(name, change))
                            Logger.debug(s"Sending approved name change to $name")
                            sender ! Name(name, room, change)
                    }
                case None =>
                    // Nobody is in this room...?
            }
        case Message(name: String, room: String, message: String) =>
            Logger.debug(s"Received a message: $name, $room, $message")
            registry get room match {
                case Some(users: Map[String, ActorRef]) =>
                    Logger.debug(s"Sending message to users in $room")
                    for ((username: String, ref: ActorRef) <- users) {
                        ref ! Message(name, room, message)
                    }
                case None =>
                    Logger.debug(s"$name is attempting to message $room which doesn't exist")
                    self forward Join(room)
            }
    }
}