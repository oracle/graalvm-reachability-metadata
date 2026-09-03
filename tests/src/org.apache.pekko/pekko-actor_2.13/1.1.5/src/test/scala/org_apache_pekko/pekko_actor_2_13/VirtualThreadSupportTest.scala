/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_actor_2_13

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.Actor
import org.apache.pekko.actor.ActorRef
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.actor.Props
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class VirtualThreadSupportTest {
  @Test
  def dispatchesActorMessagesOnVirtualThreadExecutor(): Unit = {
    val config: Config = ConfigFactory.parseString("""
        virtual-thread-dispatcher {
          type = "Dispatcher"
          executor = "virtual-thread-executor"
          throughput = 1
        }
        """)
    val system: ActorSystem = ActorSystem("virtual-thread-support", config)
    val result: CompletableFuture[ThreadObservation] = new CompletableFuture[ThreadObservation]

    try {
      val actor: ActorRef = system.actorOf(
        Props(new ThreadObservationActor(result)).withDispatcher("virtual-thread-dispatcher"),
        "observer")
      actor ! ObserveThread

      val observation: ThreadObservation = result.get(10, TimeUnit.SECONDS)
      assertThat(observation.virtual).isTrue
    } finally {
      system.terminate()
      Await.result(system.whenTerminated, 10.seconds)
    }
  }
}

final case object ObserveThread

final case class ThreadObservation(virtual: Boolean)

final class ThreadObservationActor(result: CompletableFuture[ThreadObservation]) extends Actor {
  override def receive: Receive = {
    case ObserveThread =>
      val current: Thread = Thread.currentThread()
      result.complete(ThreadObservation(current.isVirtual))
  }
}
