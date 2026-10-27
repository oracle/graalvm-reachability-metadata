/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_actor_3

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import org.apache.pekko.actor.Actor
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.actor.Props
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class ReflectTest {
  @Test
  def createsActorThroughPublicNoArgReflectConstructor(): Unit = {
    val system: ActorSystem = ActorSystem(s"reflect-public-${System.nanoTime()}")
    try {
      system.actorOf(Props[NoArgReflectActor]())

      assertThat(NoArgReflectActor.constructed.await(5, TimeUnit.SECONDS)).isTrue()
    } finally {
      Await.result(system.terminate(), 5.seconds)
    }
  }

  @Test
  def createsActorThroughPrivateNoArgReflectConstructor(): Unit = {
    val system: ActorSystem = ActorSystem(s"reflect-private-${System.nanoTime()}")
    try {
      system.actorOf(Props(classOf[PrivateConstructorReflectActor]))

      assertThat(PrivateConstructorReflectActor.constructed.await(5, TimeUnit.SECONDS)).isTrue()
    } finally {
      Await.result(system.terminate(), 5.seconds)
    }
  }

  @Test
  def createsActorThroughMatchingArgumentReflectConstructor(): Unit = {
    ArgsReflectActor.arguments.set(null)
    val system: ActorSystem = ActorSystem(s"reflect-args-${System.nanoTime()}")
    try {
      system.actorOf(Props(classOf[ArgsReflectActor], "configured", 42))

      assertThat(ArgsReflectActor.constructed.await(5, TimeUnit.SECONDS)).isTrue()
      assertThat(ArgsReflectActor.arguments.get()).isEqualTo("configured:42")
    } finally {
      Await.result(system.terminate(), 5.seconds)
    }
  }

  @Test
  def createsActorSystemWhenContextClassLoaderIsUnavailable(): Unit = {
    val currentThread: Thread = Thread.currentThread()
    val previousClassLoader: ClassLoader = currentThread.getContextClassLoader
    var system: ActorSystem = null
    currentThread.setContextClassLoader(null)
    try {
      system = ActorSystem(s"reflect-loader-${System.nanoTime()}")

      assertThat(system.name).startsWith("reflect-loader")
    } finally {
      if (system != null) {
        Await.result(system.terminate(), 5.seconds)
      }
      currentThread.setContextClassLoader(previousClassLoader)
    }
  }
}

object NoArgReflectActor {
  val constructed: CountDownLatch = new CountDownLatch(1)
}

class NoArgReflectActor extends Actor {
  NoArgReflectActor.constructed.countDown()

  override def receive: Receive = Actor.emptyBehavior
}

object PrivateConstructorReflectActor {
  val constructed: CountDownLatch = new CountDownLatch(1)
}

class PrivateConstructorReflectActor private () extends Actor {
  PrivateConstructorReflectActor.constructed.countDown()

  override def receive: Receive = Actor.emptyBehavior
}

object ArgsReflectActor {
  val arguments: AtomicReference[String] = new AtomicReference[String]()
  val constructed: CountDownLatch = new CountDownLatch(1)
}

class ArgsReflectActor(label: String, count: Int) extends Actor {
  ArgsReflectActor.arguments.set(s"$label:$count")
  ArgsReflectActor.constructed.countDown()

  override def receive: Receive = Actor.emptyBehavior
}
