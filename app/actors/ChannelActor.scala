package actors

import java.util

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.event.LoggingReceive
import akka.actor.ActorRef
import akka.actor.Terminated
import play.libs.Akka
import akka.actor.Props
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class ChannelActor extends Actor with ActorLogging {
  var onlineUsers = Map[ActorRef, String]()

  var posts = List[Message]()

  def receive = LoggingReceive {
    /**
     * Send this message to the each user which registered to this channel
     */
    case m: Message =>
      posts +:= m

      onlineUsers.keys foreach (_ ! m)

    case m: StatusUserTyping => onlineUsers.keys foreach (_ ! m)

    /**
     * Subscribe new user to this channel
     */
    case Subscribe(uid) =>
      onlineUsers += sender -> uid

      //watch the sender actor for Termination so we can deRegister them
      context watch sender

      //send last 300 message for each channel to the new User
      //todo send only users subscribed channels
      sender ! Message("_replyingChannelHistory_STARTED", "mark")
      (posts groupBy (_.channel) flatMap (_._2 take 300) toList).reverse foreach (sender ! _)
      sender ! Message("_replyingChannelHistory_FINISHED", "mark")

      //      sender ! Message("Cowboy Bebop (BOT)", "Gotta Knock a Little Harder!")
      //      sender ! Message("Shebang", s"Hello welcome back #!")

      //send status to every online user
      onlineUsers.keys foreach (_ ! Message("_userStatusChanged_ONLINE", uid))
      //send every other online users status to sender
      onlineUsers.values.filterNot(_ == uid) foreach (sender ! Message("_userStatusChanged_ONLINE", _))


    /**
     * broadcast number of online users
     */
    case BroadcastStatus =>
      onlineUsers.keys foreach (_ ! Message("_numberOfOnlineUsers", s"${onlineUsers.size}"))


    /**
     * deRegister the user from this channel on user (UserActor) termination.
     */
    case Terminated(user) => onlineUsers.get(user).map { uid =>
      onlineUsers -= user
      onlineUsers foreach { case (userActor, _) => userActor ! Message("_userStatusChanged_OFFLINE", uid)}
    }


  }

  /**
   * Every 30 seconds broadcast number of online users
   */
  override def preStart() = {
    context.system.scheduler.schedule(3 seconds, 60 seconds, self, BroadcastStatus)
  }

  ChannelActor()

}


object ChannelActor {
  lazy val channel = Akka.system().actorOf(Props[ChannelActor])
  lazy val generalChannel = Akka.system().actorOf(Props[ChannelActor])
  lazy val generalChannel2 = Akka.system().actorOf(Props[ChannelActor])

  def apply() = channel
}

case class Message(uid: String, s: String, channel: String = "", ts: Long = System.currentTimeMillis)

case class StatusUserTyping(uid: String, isTyping: Boolean, channel: String = "")

case class UserList(users: List[String])

case class Subscribe(uid: String)

object BroadcastStatus