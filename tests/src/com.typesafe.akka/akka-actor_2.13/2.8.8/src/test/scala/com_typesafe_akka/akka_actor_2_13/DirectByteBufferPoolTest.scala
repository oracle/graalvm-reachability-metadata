/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_actor_2_13

import java.nio.ByteBuffer

import akka.actor.ActorSystem
import akka.io.BufferPool
import akka.io.Tcp
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class DirectByteBufferPoolTest {
  @Test
  def releasesDirectTcpBufferWhenPoolIsFull(): Unit = {
    val config: Config = ConfigFactory.parseString("""
      akka.io.tcp.direct-buffer-size = 16 B
      akka.io.tcp.direct-buffer-pool-limit = 0
      """)

    withActorSystem("direct-byte-buffer-pool", config) { system: ActorSystem =>
      val bufferPool: BufferPool = Tcp(system).bufferPool
      val buffer: ByteBuffer = bufferPool.acquire()

      assertThat(buffer.isDirect).isTrue()
      assertThat(buffer.capacity()).isEqualTo(16)

      bufferPool.release(buffer)
    }
  }

  private def withActorSystem(name: String, config: Config)(body: ActorSystem => Unit): Unit = {
    val system: ActorSystem = ActorSystem(name, config)
    try {
      body(system)
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }
}
