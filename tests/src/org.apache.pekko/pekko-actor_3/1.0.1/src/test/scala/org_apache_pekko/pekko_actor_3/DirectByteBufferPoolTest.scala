/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_actor_3

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.io.BufferPool
import org.apache.pekko.io.Tcp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DirectByteBufferPoolTest {
  @Test
  def cleansDirectBufferWhenTcpPoolIsFull(): Unit = {
    val config: Config = ConfigFactory.parseString("""
      pekko.loglevel = "OFF"
      pekko.stdout-loglevel = "OFF"
      pekko.io.tcp.direct-buffer-size = 64 B
      pekko.io.tcp.direct-buffer-pool-limit = 0
      """)

    val system: ActorSystem = ActorSystem("direct-byte-buffer-pool", config)
    try {
      val pool: BufferPool = Tcp(system).bufferPool
      val buffer = pool.acquire()

      assertTrue(buffer.isDirect, "TCP buffer pool must allocate direct byte buffers")
      assertEquals(64, buffer.capacity())

      buffer.put(1.toByte)
      pool.release(buffer)
    } finally Await.result(system.terminate(), 10.seconds)
  }
}
