/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_actor_2_13

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.ActorSystem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.Await
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt

class VirtualThreadSupportTest {
  @Test
  def virtualThreadExecutorRunsTasksOnVirtualThreads(): Unit = {
    val config: Config = ConfigFactory.parseString("""
      pekko.actor.default-dispatcher {
        executor = "virtual-thread-executor"
        throughput = 1
      }
      """)
    val system: ActorSystem = ActorSystem("virtual-thread-default-scheduler", config)

    try {
      val observedThread: Thread = executeOnDispatcher(system.dispatcher)

      assertThat(observedThread.isVirtual).isTrue
      assertThat(observedThread.getName).contains("virtual-thread")
    } finally {
      terminate(system)
    }
  }

  @Test
  def virtualizedForkJoinDispatcherRunsTasksOnVirtualThreads(): Unit = {
    val config: Config = ConfigFactory.parseString("""
      pekko.actor.default-dispatcher.fork-join-executor.parallelism-min = 1
      pekko.actor.default-dispatcher.fork-join-executor.parallelism-max = 1
      pekko.actor.default-dispatcher.fork-join-executor.parallelism-factor = 1.0

      virtualized-fork-join-dispatcher {
        type = "Dispatcher"
        executor = "fork-join-executor"
        fork-join-executor {
          parallelism-min = 1
          parallelism-max = 1
          parallelism-factor = 1.0
          virtualize = on
        }
        throughput = 1
      }
      """)
    val system: ActorSystem = ActorSystem("virtualized-fork-join-scheduler", config)

    try {
      val dispatcher: ExecutionContextExecutor = system.dispatchers.lookup("virtualized-fork-join-dispatcher")
      val observedThread: Thread = executeOnDispatcher(dispatcher)

      assertThat(observedThread.isVirtual).isTrue
      assertThat(observedThread.getName).contains("virtual-thread")
    } finally {
      terminate(system)
    }
  }

  private def executeOnDispatcher(dispatcher: ExecutionContextExecutor): Thread = {
    val latch: CountDownLatch = new CountDownLatch(1)
    val observedThread: AtomicReference[Thread] = new AtomicReference[Thread]()

    dispatcher.execute(new Runnable {
      override def run(): Unit = {
        observedThread.set(Thread.currentThread())
        latch.countDown()
      }
    })

    assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue
    assertThat(observedThread.get()).isNotNull
    observedThread.get()
  }

  private def terminate(system: ActorSystem): Unit = {
    Await.result(system.terminate(), 10.seconds)
  }
}
