package com.ambantis.pub

import akka.actor.ActorSystem
import akka.kernel.Bootable
import com.typesafe.config.ConfigFactory

/**
 * PublisherKernel
 * User: Alexandros Bantis
 * Date: 8/17/14
 * Time: 4:09 PM
 */
object PublisherBoot extends Bootable {

  lazy val config = ConfigFactory.load("publisher.conf")
  lazy val name = config.getString("app.pub.name")
  lazy val system = ActorSystem(name, config)

  def startup() = {
    val eventBus = system.actorOf(EventBusActor.props(), "EventBus")
    system.actorOf(TimeAnnouncer.props(eventBus), "Announcer")
  }

  def shutdown() = {
    system.shutdown()
  }
}
