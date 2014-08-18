package com.ambantis

import com.ambantis.pub.PublisherBoot
import com.ambantis.sub.SubscriberBoot

/**
 * Startup
 * User: Alexandros Bantis
 * Date: 8/17/14
 * Time: 9:45 PM
 */
object Startup extends App {

  val errMsg = "usage: `sub` for a subscriber and `pub` for publisher"
  println(s"starting up application with args: $args")

  args.toList match {
    case a :: as if a == "sub" => SubscriberBoot.startup()
    case a :: as if a == "pub" => PublisherBoot.startup()
    case _                     => println(errMsg)
  }
}
