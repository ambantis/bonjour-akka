package com.ambantis.sub

import com.ambantis.pub.TimeAnnouncer.Announcement

import akka.actor.{Props, ActorRef, Actor, ActorLogging}
import akka.event.LoggingReceive
import com.ambantis.sub.Subscriber.LetTheGamesBegin
import scala.concurrent.duration._

object Subscriber {
  case object LetTheGamesBegin
  def props(handler: ActorRef) = Props(classOf[Subscriber], handler)
}

/**
 * Subscriber
 * User: Alexandros Bantis
 * Date: 8/17/14
 * Time: 2:34 PM
 */
class Subscriber(handler: ActorRef) extends Actor with ActorLogging with BonjourConnectionSupport {

  override def preStart(): Unit = {
    super[Actor].preStart()
    if (existsBonjourEndpoints) LetTheGamesBegin
    else self ! Announcement("sorry, can't find Bonjour Endpoints, there will be no announcements")
  }

  def receive = LoggingReceive {
    case LetTheGamesBegin =>
      log.info(s"subscriber actor system coming online")
      handler ! startMsgForBonjour(self)
    case Announcement(msg) =>
      log.debug(s"${self.path} received msg $msg")
    case msg =>
      log.info(s"${self.path} received unknown msg $msg")
  }
}
