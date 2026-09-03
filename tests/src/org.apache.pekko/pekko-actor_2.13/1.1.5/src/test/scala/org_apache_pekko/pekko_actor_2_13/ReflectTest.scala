/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_actor_2_13

import org.apache.pekko.actor.Actor
import org.apache.pekko.actor.ActorRef
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.actor.Props
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class ReflectTest {
  @Test
  def instantiatesActorWithPrivateNoArgConstructor(): Unit = {
    val system: ActorSystem = ActorSystem("reflect-private-no-arg-constructor")
    val response: CompletableFuture[String] = new CompletableFuture[String]
    PrivateNoArgReflectActor.response.set(response)

    try {
      val actor: ActorRef = system.actorOf(Props(classOf[PrivateNoArgReflectActor]), "private-no-arg")
      actor ! ReflectPing

      assertThat(response.get(10, TimeUnit.SECONDS)).isEqualTo("pong")
    } finally {
      PrivateNoArgReflectActor.response.set(null)
      system.terminate()
      Await.result(system.whenTerminated, 10.seconds)
    }
  }

  @Test
  def startsActorSystemWithoutThreadContextClassLoader(): Unit = {
    val originalClassLoader: ClassLoader = Thread.currentThread().getContextClassLoader
    var system: ActorSystem = null

    try {
      Thread.currentThread().setContextClassLoader(null)
      system = ActorSystem("reflect-context-class-loader-fallback")

      assertThat(system.name).isEqualTo("reflect-context-class-loader-fallback")
    } finally {
      Thread.currentThread().setContextClassLoader(originalClassLoader)
      if (system != null) {
        system.terminate()
        Await.result(system.whenTerminated, 10.seconds)
      }
    }
  }
}

case object ReflectPing

object PrivateNoArgReflectActor {
  val response: AtomicReference[CompletableFuture[String]] = new AtomicReference[CompletableFuture[String]]
}

final class PrivateNoArgReflectActor private () extends Actor {
  override def receive: Receive = {
    case ReflectPing =>
      val currentResponse: CompletableFuture[String] = PrivateNoArgReflectActor.response.get()
      if (currentResponse != null) {
        currentResponse.complete("pong")
      }
  }
}
