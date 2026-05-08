/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_actor_3

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

import org.apache.pekko.actor.Actor
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.actor.Props
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReflectTest {
  @Test
  def createsActorWithPublicNoArgumentConstructor(): Unit = {
    withActorSystem("reflect-public-no-arg") { system =>
      system.actorOf(Props(classOf[ReflectPublicNoArgumentActor]))

      assertTrue(
        ReflectPublicNoArgumentActor.created.await(10, TimeUnit.SECONDS),
        "public no-argument actor was not created")
    }
  }

  @Test
  def createsActorWithPrivateNoArgumentConstructor(): Unit = {
    withActorSystem("reflect-private-no-arg") { system =>
      system.actorOf(Props(classOf[ReflectPrivateNoArgumentActor]))

      assertTrue(
        ReflectPrivateNoArgumentActor.created.await(10, TimeUnit.SECONDS),
        "private no-argument actor was not created")
    }
  }

  @Test
  def createsActorWithConstructorArguments(): Unit = {
    val constructorArgument: String = "constructor-argument"

    withActorSystem("reflect-arg") { system =>
      system.actorOf(Props(classOf[ReflectStringArgumentActor], constructorArgument))

      assertTrue(
        ReflectStringArgumentActor.created.await(10, TimeUnit.SECONDS),
        "argument actor was not created")
      assertEquals(constructorArgument, ReflectStringArgumentActor.lastValue)
    }
  }

  private def withActorSystem(name: String)(testBody: ActorSystem => Unit): Unit = {
    val system: ActorSystem = ActorSystem(name)
    try testBody(system)
    finally Await.result(system.terminate(), 10.seconds)
  }
}

class ReflectPublicNoArgumentActor extends Actor {
  ReflectPublicNoArgumentActor.created.countDown()

  override def receive: Receive = Actor.emptyBehavior
}

object ReflectPublicNoArgumentActor {
  val created: CountDownLatch = new CountDownLatch(1)
}

class ReflectPrivateNoArgumentActor private () extends Actor {
  ReflectPrivateNoArgumentActor.created.countDown()

  override def receive: Receive = Actor.emptyBehavior
}

object ReflectPrivateNoArgumentActor {
  val created: CountDownLatch = new CountDownLatch(1)
}

class ReflectStringArgumentActor(value: String) extends Actor {
  ReflectStringArgumentActor.lastValue = value
  ReflectStringArgumentActor.created.countDown()

  override def receive: Receive = Actor.emptyBehavior
}

object ReflectStringArgumentActor {
  val created: CountDownLatch = new CountDownLatch(1)
  @volatile var lastValue: String = ""
}
