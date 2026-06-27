/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_actor_2_13

import akka.actor.ActorSystem
import akka.io.BufferPool
import akka.io.Tcp
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import java.nio.ByteBuffer
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class DirectByteBufferPoolTest {
  @Test
  def releasesOverflowDirectBufferThroughTcpExtensionPool(): Unit = {
    val system: ActorSystem = ActorSystem("direct-byte-buffer-pool", tcpBufferPoolConfig)

    try {
      val pool: BufferPool = Tcp(system).bufferPool
      val buffer: ByteBuffer = pool.acquire()

      assertThat(buffer.isDirect).isTrue
      pool.release(buffer)
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }

  private def tcpBufferPoolConfig: Config = ConfigFactory.parseString("""
      akka.loglevel = "ERROR"
      akka.stdout-loglevel = "ERROR"
      akka.actor.default-dispatcher.shutdown-timeout = 10s
      akka.coordinated-shutdown.run-by-jvm-shutdown-hook = off
      akka.io.tcp.direct-buffer-pool-limit = 0
      akka.io.tcp.direct-buffer-size = 1024 B
      akka.io.tcp.register-timeout = 10s
      """).withFallback(ConfigFactory.load())
}
