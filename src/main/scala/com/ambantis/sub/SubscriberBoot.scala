package com.ambantis.sub

import akka.actor.ActorSystem
import akka.kernel.Bootable
import com.typesafe.config.ConfigFactory

/**
 * BootSubscriber
 * User: Alexandros Bantis
 * Date: 8/17/14
 * Time: 4:49 PM
 */
object SubscriberBoot extends Bootable {

  lazy val config = ConfigFactory.load()
  lazy val name = config.getString("app.sub.name")
  lazy val system = ActorSystem(name, config)

  def startup(): Unit = {
    val connectionHandler = system.actorOf(ConnectionHandler.props(), "ConnectionHandler")
    val subscriber = system.actorOf(Subscriber.props(connectionHandler), "Subscriber")
    subscriber ! Subscriber.LetTheGamesBegin
  }

  def shutdown() = {
    system.shutdown()
  }
}
