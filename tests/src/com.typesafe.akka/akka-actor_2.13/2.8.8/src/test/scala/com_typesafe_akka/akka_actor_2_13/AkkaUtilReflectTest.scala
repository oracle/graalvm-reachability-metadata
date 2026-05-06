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
import akka.actor.ActorSystem
import akka.actor.Props
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class AkkaUtilReflectTest {
  @Test
  def createsActorWithPublicNoArgumentConstructor(): Unit = {
    val latch: CountDownLatch = ReflectPublicNoArgsActor.expectCreation()

    withActorSystem("reflect-public-no-args") { system: ActorSystem =>
      system.actorOf(Props(classOf[ReflectPublicNoArgsActor]), "public-no-args")
      assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue()
    }
  }

  @Test
  def createsActorWithPrivateNoArgumentConstructor(): Unit = {
    val latch: CountDownLatch = ReflectPrivateNoArgsActor.expectCreation()

    withActorSystem("reflect-private-no-args") { system: ActorSystem =>
      system.actorOf(Props(classOf[ReflectPrivateNoArgsActor]), "private-no-args")
      assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue()
    }
  }

  @Test
  def createsActorWithConstructorArguments(): Unit = {
    val latch: CountDownLatch = new CountDownLatch(1)

    withActorSystem("reflect-args") { system: ActorSystem =>
      system.actorOf(Props(classOf[ReflectArgsActor], "constructed", latch), "args")
      assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue()
    }
  }

  @Test
  def createsActorSystemWhenContextClassLoaderIsUnavailable(): Unit = {
    val thread: Thread = Thread.currentThread()
    val originalClassLoader: ClassLoader = thread.getContextClassLoader

    thread.setContextClassLoader(null)
    try {
      withActorSystem("reflect-class-loader") { system: ActorSystem =>
        assertThat(system.name).isEqualTo("reflect-class-loader")
      }
    } finally {
      thread.setContextClassLoader(originalClassLoader)
    }
  }

  private def withActorSystem(name: String)(body: ActorSystem => Unit): Unit = {
    val system: ActorSystem = ActorSystem(name)
    try {
      body(system)
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }
}

class ReflectPublicNoArgsActor extends Actor {
  ReflectPublicNoArgsActor.markCreated()

  override def receive: Receive = Actor.emptyBehavior
}

object ReflectPublicNoArgsActor {
  private val creationLatch: AtomicReference[CountDownLatch] = new AtomicReference[CountDownLatch]()

  def expectCreation(): CountDownLatch = {
    val latch: CountDownLatch = new CountDownLatch(1)
    creationLatch.set(latch)
    latch
  }

  def markCreated(): Unit = {
    val latch: CountDownLatch = creationLatch.get()
    if (latch != null) {
      latch.countDown()
    }
  }
}

class ReflectPrivateNoArgsActor private () extends Actor {
  ReflectPrivateNoArgsActor.markCreated()

  override def receive: Receive = Actor.emptyBehavior
}

object ReflectPrivateNoArgsActor {
  private val creationLatch: AtomicReference[CountDownLatch] = new AtomicReference[CountDownLatch]()

  def expectCreation(): CountDownLatch = {
    val latch: CountDownLatch = new CountDownLatch(1)
    creationLatch.set(latch)
    latch
  }

  def markCreated(): Unit = {
    val latch: CountDownLatch = creationLatch.get()
    if (latch != null) {
      latch.countDown()
    }
  }
}

class ReflectArgsActor(marker: String, latch: CountDownLatch) extends Actor {
  if (marker == "constructed") {
    latch.countDown()
  }

  override def receive: Receive = Actor.emptyBehavior
}
