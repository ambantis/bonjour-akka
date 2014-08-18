package com.ambantis.sub

import akka.actor.SupervisorStrategy.Restart
import akka.remote.transport.AssociationHandle.{Quarantined, Unknown, Disassociated, Shutdown}
import akka.remote.{QuarantinedEvent, AssociationErrorEvent, RemotingLifecycleEvent}
import com.ambantis.pub.TimeAnnouncer.Announcement
import com.ambantis.pub.EventBusActor.{Unsubscribe, Subscribe}
import com.ambantis.sub.ConnectionHandler.{StopConnection, StartConnection, ConnectionMsg}

import akka.actor._
import akka.event.LoggingReceive
import scala.concurrent.duration._

/**
 * A class that mixes in BonjourConnectionSupport inherits methods that can create StartConnection & StopConnection
 * messages that the ConnectionHandler can use to Start/Stop subscriptions to the Bonjour publisher event stream
 */
trait BonjourConnectionSupport { this: Actor with ActorLogging =>
  import scala.collection.JavaConversions._

  private val endpointsConfigPath = "app.sub.endpoints"
  private val endpoints: List[ActorPath] = {
    try {
      context.system.settings.config.getStringList(endpointsConfigPath).toList.map(ActorPath.fromString)}
    catch {
      case e: Throwable =>
        log.error(s"${self.path} unable to load Bonjour endpoints from path $endpointsConfigPath", e)
        List.empty[ActorPath]
    }
  }

  /**
   * Indicates whether it is possible to attempt a connection to one or more JDLink dispatcher(s)
   *
   * @return true if calling the method `startMsgForDispatcherJDLink` will create a StartConnection message with ActorPaths
   */
  def existsBonjourEndpoints: Boolean = endpoints.nonEmpty

  /**
   * Helper method to construct a subscribe message for JDLink dispatchers
   *
   * @param subscriber the ActorRef that should be subscribed to the JDLink dispatcher(s)
   * @return `StartConnection` object that can be sent to a ConnectionHandler to initiate a subscription to JDLink dispatcher(s)
   */
  def startMsgForBonjour(subscriber: ActorRef) = {
    val msg = Subscribe(subscriber, List(classOf[Announcement]))
    StartConnection(endpoints, msg)
  }

  /**
   * A Helper method to construct an `UnSubscribe` message for JDLink dispatcher(s)
   *
   * @param subscriber the ActorRef that should be unsubscribed from the JDLink dispatcher(s)
   * @return `StopConnection` object that can be sent to a ConnectionHandler to end a subscription to JDLink dispatcher(s)
   */
  def stopMsgForBonjour(subscriber: ActorRef) = {
    val msg = Unsubscribe(subscriber, List(classOf[Announcement]))
    StopConnection(endpoints.headOption, msg)
  }
}

/**
 * The public api of the ConnectionHandler actor
 */
object ConnectionHandler {

  /**
   * A command to create a connection with a list of ActorPaths and send the `startMsg` upon connection to them.
   *
   * A start connection takes a list of ActorPaths, starts a connection with all of the paths, and when a connection
   * is established, sends the startMsg to them. All of the paths must must represents different endpoints to the same
   * actor (i.e., have different hostName/port combinations, but the same relative actor path and system).
   *
   * @param paths a list of ActorPaths that can be used to reference the remote actor.
   * @param startMsg the message that should be sent to the remote actor once a connection is established.
   */
  case class StartConnection(paths: List[ActorPath], startMsg: Any)

  /**
   * A command to forward a message to an existing connection with remote actor(s).
   *
   * @param path an Option[ActorPath] used to reference a unique combination of actor system & relative path (host/post not necessary)
   * @param msg the message that should be sent to the remote actor(s), once a connection is established.
   */
  case class ConnectionMsg(path: Option[ActorPath], msg: Any)

  /**
   * A command to terminate an existing connection with a remote actor, sending the `endMsg` and then stopping the child
   * connection actor.
   *
   * A stop connection takes a single ActorPath and a message to forward to the path's endpoint. Note that a single child
   * actor of ConnectionHandler manages a set of ActorPaths that all represent a unique combination of actor system and
   * relative path. Therefore, it is not necessary to include a host/port as part of the argument. Once the message is
   * successfully forwarded to the remote actor, the child of ConnectionHandler managing this specific connection will
   * stop and its state must be updated.
   *
   * Note: if one or more of the remote ActorPaths are unreachable (e.g. the remote address is Quarantined), the
   * unsubscribe message will not be delivered.
   *
   * @param path an Option[ActorPath] used to reference a unique combination of actor system & relative path (host/port not necessary)
   * @param endMsg a message the remote endpoint will understand to terminate the connection.
   */
  case class StopConnection(path: Option[ActorPath], endMsg: Any)

  def props() = Props(classOf[ConnectionHandler])
}


/**
 * An actor for managing groups of remote connections to a given actor system & local path.
 *
 * In the event a connection cannot be established (in the form of an Identity message) to one of the paths,
 * the actor will cause a retry to be sent with increasing amounts of delays spaced between, up to a maximum wait time.
 *
 */
class ConnectionHandler extends Actor with ActorLogging {

  import com.ambantis.sub.ConnectionHandler._
  import context._

  override def postStop(): Unit = {
    log.error(s"${self.path} with class ${this.getClass} about to die")
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    log.error(s"${self.path} with class ${this.getClass} about to restart from msg: $message with error $reason")
    super[Actor].preRestart(reason, message)
  }

  override val supervisorStrategy = AllForOneStrategy(5, 5.seconds, loggingEnabled = true) {
    case e: Throwable =>
      log.error(s"${self.path} about to restart child due to $e")
      Restart
  }

  def nameFor(path: ActorPath): String = s"${path.address.system}${path.toStringWithoutAddress}".hashCode.toString
  def isChild(p: ActorPath): Boolean = children.exists(_.path.name == nameFor(p))
  def hasUniqueName(paths: List[ActorPath]): Boolean = paths.nonEmpty && {
    val headName = nameFor(paths.head)
    paths.tail.map(nameFor).forall(_ == headName)
  }

  def connect(ps: List[ActorPath]): ActorRef = actorOf(Props(classOf[Connection], ps), nameFor(ps.head))
  def forwardMsg(p: ActorPath, msg: Any): Unit = child(nameFor(p)) foreach (_ ! msg)

  def receive: Receive = LoggingReceive {
    case msg @ StartConnection(ps, _) if hasUniqueName(ps) && !isChild(ps.head) => connect(ps) ! msg
    case msg @ ConnectionMsg( Some(p), _) if isChild(p)                         => forwardMsg(p, msg)
    case msg @ StopConnection(Some(p), _) if isChild(p)                         => forwardMsg(p, msg)
    case msg @ AssociationErrorEvent(cause, localAddress, remoteAddress, inboud, logLevel) =>
      log.error(s"${self.path.name} received associationErrorEvent $msg")

    case msg => log.info(s"actor ${self.path} with children $children unable to process message ${msg.getClass}")
  }

  system.eventStream.subscribe(self, classOf[AssociationErrorEvent])
  system.eventStream.subscribe(self, classOf[RemotingLifecycleEvent])
  system.eventStream.subscribe(self, classOf[QuarantinedEvent])
}

private[sub] class Connection(paths: List[ActorPath]) extends Actor with ActorLogging {
  import context._

  override def preStart(): Unit = {
    super[Actor].preStart()
    paths.foreach(p => actorOf(Props(classOf[Endpoint], p), p.hashCode().toString))
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    log.error(s"${self.path} with class ${this.getClass} about to restart from msg: $message with error $reason")
    super[Actor].preRestart(reason, message)
  }

  override def postStop(): Unit = {
    log.error(s"${self.path} with class ${this.getClass} about to die")
  }

  override val supervisorStrategy = AllForOneStrategy(5, 5.seconds, loggingEnabled = true) {
    case e: Throwable =>
      log.error(s"${self.path} about to restart child due to $e")
      Restart
  }


  override def receive: Receive = LoggingReceive {
    case StartConnection(_,msg) => children foreach (_ ! ConnectionMsg(None,msg))
    case msg: ConnectionMsg     => children foreach (_ forward msg)
    case StopConnection(_,msg)  =>
      children.foreach(_ ! ConnectionMsg(None,msg))
      system.scheduler.scheduleOnce(1 second, self, PoisonPill)
    case Terminated(ref) => log.error(s"${self.path.name} received terminated message for $ref")
  }
}

private[sub] class Endpoint(path: ActorPath) extends Actor with ActorLogging {
  import context._

  val passCode: Long = System.nanoTime() - this.hashCode()
  val delayConfigPath = "app.delayInMillis"
  val maxDelayConfigPath = "app.maxDelayInMinutes"
  val logMsgPrefix = s"actor ${self.path}, the guardian of ActorPath $path"
  lazy val baseDelay: FiniteDuration =
    try { system.settings.config.getLong(delayConfigPath).millis }
    catch { case e: Throwable => log.error(s"unable to load path $delayConfigPath", e); 100.millis }
  lazy val maxDelay: FiniteDuration =
    try { system.settings.config.getLong(maxDelayConfigPath).minutes }
    catch { case e: Throwable => log.error(s"unable to load path $maxDelayConfigPath", e); 5.minutes }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    log.error(s"${self.path} with class ${this.getClass} about to restart from msg: $message with error $reason")
    super[Actor].preRestart(reason, message)
  }

  var timeoutMsg: Cancellable = _
  var attempts: Int = 0
  var publisher: Option[ActorRef] = None
  var messages = List.empty[Any]
  val shouldWatch: Boolean =
    try { system.settings.config.getBoolean("app.watch") } catch {
    case e: Throwable => log.error(s"${self.path} unable to load Bonjour endpoints from path `app.watch`", e); false }

  def delay: FiniteDuration = {
    val _delay = baseDelay * attempts * attempts
    if (_delay < maxDelay) _delay else maxDelay
  }

  override def preStart(): Unit = {
    super[Actor].preStart()
    sendIdentifyRequest()
  }

  override def postStop(): Unit = {
    log.error(s"${self.path} with class ${this.getClass} about to die")
  }

  def sendIdentifyRequest(): Unit = {
    log.info(s"$logMsgPrefix about to send Identity msg, have previously attempted $attempts time(s)")
    attempts += 1
    timeoutMsg = system.scheduler.scheduleOnce(delay, self, ReceiveTimeout)
    actorSelection(path) ! Identify(passCode)
  }

  def sendMessage(): Unit = {
    log.info(s"$logMsgPrefix about to send message $messages to $publisher")
    if (publisher.isDefined && messages.nonEmpty) {
      for (p <- publisher; m <- messages.reverse) p ! m
      messages = List.empty
    }
  }

  override def receive: Receive = LoggingReceive {
    case ReceiveTimeout => sendIdentifyRequest()
    case ConnectionMsg(_,msg) =>
      log.info(s"$logMsgPrefix received a connection msg with $msg")
      messages = msg :: messages
      sendMessage()
    case ActorIdentity(id, Some(pub)) if id == passCode =>
      log.info(s"$logMsgPrefix has received ActorIdentity for ref $pub")
      publisher = Some(pub)
      if (shouldWatch) watch(pub)
      timeoutMsg.cancel()
      sendMessage()
    case Terminated(ref) if publisher.isDefined && ref == publisher.get =>
      log.info(s"$logMsgPrefix has received a Terminated message for ActorRef $ref")
      attempts = 0
      sendIdentifyRequest()
    case Terminated(ref) =>
      log.info(s"$logMsgPrefix HAS RECEIVED AN UNEXPECTED TERMINATED MESSAGE FOR ACTOR_REF $ref")
  }
}
