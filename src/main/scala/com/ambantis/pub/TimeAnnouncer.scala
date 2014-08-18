package com.ambantis.pub

import akka.actor.{Props, ActorRef, Actor, ActorLogging}
import akka.event.LoggingReceive
import com.ambantis.pub.EventBusActor.Publish
import scala.concurrent.duration._

/**
 * Publisher
 * User: Alexandros Bantis
 * Date: 8/17/14
 * Time: 3:39 PM
 */

object TimeAnnouncer {
  case object Ring
  case class Announcement(msg: String)
  def props(publisher: ActorRef) = Props(classOf[TimeAnnouncer], publisher)
}


class TimeAnnouncer(publisher: ActorRef) extends Actor with ActorLogging {
  import TimeAnnouncer.{Announcement, Ring}
  import context._

  system.scheduler.schedule(5 seconds, 5 seconds, self, Ring)
  log.info(s"the Time Announcer coming online with publisher $publisher")

  def receive = LoggingReceive {
    case msg @ Ring => bonjour()
    case msg  => log.error(s"${self.path} reçu un message inconnu: $msg")
  }

  def bonjour(): Unit = {
    publisher ! Publish(Announcement(s"Bonjour! le ton, GMT sera ${System.currentTimeMillis()}"))
  }
}
