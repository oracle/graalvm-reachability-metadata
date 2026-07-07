/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_actor_2_13

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class ReflectTest {

  @Test
  def propsCreatesPublicNoArgumentActorReflectively(): Unit = {
    withActorSystem("reflect-public-no-arg") { system: ActorSystem =>
      val recorder: AtomicReference[String] = new AtomicReference[String]()
      val latch: CountDownLatch = new CountDownLatch(1)

      val actor = system.actorOf(Props(classOf[PublicNoArgActor]), "public-no-arg")
      actor.tell(new ActorMessage("created", recorder, latch), ActorRef.noSender)

      assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue()
      assertThat(recorder.get()).isEqualTo("public-created")
    }
  }

  @Test
  def propsCreatesPrivateNoArgumentActorReflectively(): Unit = {
    withActorSystem("reflect-private-no-arg") { system: ActorSystem =>
      val recorder: AtomicReference[String] = new AtomicReference[String]()
      val latch: CountDownLatch = new CountDownLatch(1)

      val actor = system.actorOf(Props(classOf[PrivateNoArgActor]), "private-no-arg")
      actor.tell(new ActorMessage("created", recorder, latch), ActorRef.noSender)

      assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue()
      assertThat(recorder.get()).isEqualTo("private-created")
    }
  }

  @Test
  def propsCreatesActorWithConstructorArgumentsReflectively(): Unit = {
    withActorSystem("reflect-constructor-args") { system: ActorSystem =>
      val recorder: AtomicReference[String] = new AtomicReference[String]()
      val latch: CountDownLatch = new CountDownLatch(1)

      val props: Props = Props(classOf[ConstructedActor], "constructed", Integer.valueOf(8))
      val actor = system.actorOf(props, "constructed")
      actor.tell(new ActorMessage("created", recorder, latch), ActorRef.noSender)

      assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue()
      assertThat(recorder.get()).isEqualTo("constructed-8-created")
    }
  }

  private def withActorSystem(name: String)(body: ActorSystem => Unit): Unit = {
    val thread: Thread = Thread.currentThread()
    val originalClassLoader: ClassLoader = thread.getContextClassLoader
    thread.setContextClassLoader(null)
    val system: ActorSystem =
      try ActorSystem(name)
      finally thread.setContextClassLoader(originalClassLoader)

    try body(system)
    finally {
      system.terminate()
      Await.result(system.whenTerminated, 10.seconds)
    }
  }
}

final class ActorMessage(
    val value: String,
    val recorder: AtomicReference[String],
    val latch: CountDownLatch)

class PublicNoArgActor extends Actor {
  override def receive: Receive = {
    case message: ActorMessage =>
      message.recorder.set(s"public-${message.value}")
      message.latch.countDown()
  }
}

class PrivateNoArgActor private () extends Actor {
  override def receive: Receive = {
    case message: ActorMessage =>
      message.recorder.set(s"private-${message.value}")
      message.latch.countDown()
  }
}

class ConstructedActor(prefix: String, number: Int) extends Actor {
  override def receive: Receive = {
    case message: ActorMessage =>
      message.recorder.set(s"$prefix-$number-${message.value}")
      message.latch.countDown()
  }
}
