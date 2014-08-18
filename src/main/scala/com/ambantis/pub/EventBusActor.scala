package com.ambantis.pub

import akka.actor.{Props, ActorRef, ActorLogging, Actor}
import akka.event.{LoggingReceive, EventStream}

/**
 * EventBusActor
 * User: Alexandros Bantis
 * Date: 8/17/14
 * Time: 3:15 PM
 */

object EventBusActor {
  case class Subscribe(ref: ActorRef, msgs: List[Class[_ <: AnyRef]])
  case class Unsubscribe(ref: ActorRef, msgs: List[Class[_ <: AnyRef]])
  case class Publish(msg: AnyRef)
  def props() = Props(classOf[EventBusActor], new EventStream(debug = true))
}

class EventBusActor(es: EventStream) extends Actor with ActorLogging {
  import EventBusActor.{Subscribe, Unsubscribe, Publish}

  var sub: Option[ActorRef] = None

  def receive = LoggingReceive {
    case msg @ Subscribe(ref, ms) =>
      log.info(s"${self.path} received a message $msg")
      ms foreach (es.subscribe(ref, _))
      sub = Some(ref)
    case Unsubscribe(ref, ms) => ms foreach { es.unsubscribe(ref, _) }
    case Publish(msg) =>
      log.info(s"about to publish $msg to $es")
      es.publish(msg)
  }
}
