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
    def receive = {
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
            }
    }
}
