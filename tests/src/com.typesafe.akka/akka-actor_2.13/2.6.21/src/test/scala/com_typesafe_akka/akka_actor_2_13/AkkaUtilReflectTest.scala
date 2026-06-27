/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_actor_2_13

import akka.actor.Actor
import akka.actor.ActorSystem
import akka.actor.Props
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class AkkaUtilReflectTest {
  @Test
  def instantiatesPublicNoArgumentActorThroughProps(): Unit = {
    val signal: CountDownLatch = new CountDownLatch(1)
    ReflectActorSignals.publicNoArgumentSignal = signal
    val system: ActorSystem = ActorSystem("reflect-public-no-argument", quietConfig)

    try {
      val actor = system.actorOf(Props(classOf[PublicNoArgumentReflectActor]), "public-no-argument")
      actor ! ReflectActorSignals.Ping

      assertThat(signal.await(10L, TimeUnit.SECONDS)).isTrue()
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }

  @Test
  def instantiatesPrivateNoArgumentActorThroughProps(): Unit = {
    val signal: CountDownLatch = new CountDownLatch(1)
    ReflectActorSignals.privateNoArgumentSignal = signal
    val system: ActorSystem = ActorSystem("reflect-private-no-argument", quietConfig)

    try {
      val actor = system.actorOf(Props(classOf[PrivateNoArgumentReflectActor]), "private-no-argument")
      actor ! ReflectActorSignals.Ping

      assertThat(signal.await(10L, TimeUnit.SECONDS)).isTrue()
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }

  @Test
  def instantiatesConstructorArgumentActorThroughProps(): Unit = {
    val signal: CountDownLatch = new CountDownLatch(1)
    val system: ActorSystem = ActorSystem("reflect-constructor-arguments", quietConfig)

    try {
      val actor = system.actorOf(
        Props(classOf[ConstructorArgumentReflectActor], ReflectActorSignals.Ping, signal),
        "constructor-arguments")
      actor ! ReflectActorSignals.Ping

      assertThat(signal.await(10L, TimeUnit.SECONDS)).isTrue()
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }

  @Test
  def createsActorSystemWhenContextClassLoaderIsAbsent(): Unit = {
    val config: Config = quietConfig
    val originalClassLoader: ClassLoader = Thread.currentThread().getContextClassLoader
    var system: ActorSystem = null
    Thread.currentThread().setContextClassLoader(null)

    try {
      system = ActorSystem("reflect-find-class-loader", config)
      assertThat(system.name).isEqualTo("reflect-find-class-loader")
    } finally {
      try {
        if (system != null) Await.result(system.terminate(), 10.seconds)
      } finally Thread.currentThread().setContextClassLoader(originalClassLoader)
    }
  }

  private def quietConfig: Config = ConfigFactory.parseString("""
      akka.loglevel = "ERROR"
      akka.stdout-loglevel = "ERROR"
      akka.actor.default-dispatcher.shutdown-timeout = 10s
      akka.coordinated-shutdown.run-by-jvm-shutdown-hook = off
      """).withFallback(ConfigFactory.load())
}

object ReflectActorSignals {
  val Ping: String = "ping"
  @volatile var publicNoArgumentSignal: CountDownLatch = _
  @volatile var privateNoArgumentSignal: CountDownLatch = _
}

final class PublicNoArgumentReflectActor extends Actor {
  override def receive: Receive = {
    case ReflectActorSignals.Ping => ReflectActorSignals.publicNoArgumentSignal.countDown()
  }
}

final class PrivateNoArgumentReflectActor private () extends Actor {
  override def receive: Receive = {
    case ReflectActorSignals.Ping => ReflectActorSignals.privateNoArgumentSignal.countDown()
  }
}

final class ConstructorArgumentReflectActor(expectedMessage: String, signal: CountDownLatch) extends Actor {
  override def receive: Receive = {
    case message: String if message == expectedMessage => signal.countDown()
  }
}
