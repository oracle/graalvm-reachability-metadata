/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_3

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import zio.internal.LoomSupport

@Timeout(10)
final class LoomSupportTest {
  @Test
  def startsVirtualThreadThroughLoomSupport(): Unit = {
    val currentThread: AtomicReference[Thread] = new AtomicReference[Thread]()
    val completed: CountDownLatch = new CountDownLatch(1)

    val started: Boolean = LoomSupport.createVirtualThread(new Runnable {
      override def run(): Unit = {
        currentThread.set(Thread.currentThread())
        completed.countDown()
      }
    })

    assertThat(started).isTrue()
    assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue()
    assertThat(currentThread.get()).isNotNull()
    assertThat(currentThread.get().isVirtual).isTrue()
  }

  @Test
  def createsVirtualThreadPerTaskExecutorThroughLoomSupport(): Unit = {
    val executorService: ExecutorService = LoomSupport
      .newVirtualThreadPerTaskExecutor()
      .get
      .asInstanceOf[ExecutorService]

    try {
      val isVirtualThread: Future[Boolean] = executorService.submit(new Callable[Boolean] {
        override def call(): Boolean = Thread.currentThread().isVirtual
      })

      assertThat(isVirtualThread.get(5, TimeUnit.SECONDS)).isTrue()
    } finally {
      executorService.shutdown()
      if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
        executorService.shutdownNow()
        executorService.awaitTermination(2, TimeUnit.SECONDS)
      }
    }
  }
}
