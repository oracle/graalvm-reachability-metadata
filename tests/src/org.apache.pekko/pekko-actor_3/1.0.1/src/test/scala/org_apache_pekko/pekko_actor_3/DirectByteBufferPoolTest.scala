/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_actor_3

import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.Method
import java.nio.ByteBuffer

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.io.BufferPool
import org.apache.pekko.io.Tcp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class DirectByteBufferPoolTest {
  @Test
  def releasesDirectBufferWhenTcpExtensionPoolIsFull(): Unit = {
    val config: Config = ConfigFactory.parseString("""
      pekko.io.tcp.direct-buffer-size = 64 bytes
      pekko.io.tcp.direct-buffer-pool-limit = 0
      """)
    val system: ActorSystem = ActorSystem(s"direct-byte-buffer-pool-${System.nanoTime()}", config)
    try {
      val bufferPool: BufferPool = Tcp(system).bufferPool
      val buffer: ByteBuffer = bufferPool.acquire()

      assertThat(buffer.isDirect).isTrue()
      assertThat(buffer.capacity()).isEqualTo(64)

      bufferPool.release(buffer)
    } finally {
      Await.result(system.terminate(), 5.seconds)
    }
  }

  @Test
  def invokesDirectBufferCleanupClosure(): Unit = {
    val directBufferPoolModuleClass: Class[?] = Class.forName("org.apache.pekko.io.DirectByteBufferPool$")
    val directBufferPoolModule: AnyRef = directBufferPoolModuleClass.getField("MODULE$").get(null)
    val lookup: MethodHandles.Lookup = MethodHandles.privateLookupIn(directBufferPoolModuleClass, MethodHandles.lookup())
    val methodType: MethodType = MethodType.methodType(
      java.lang.Void.TYPE,
      classOf[Method],
      classOf[Method],
      classOf[ByteBuffer]
    )
    val cleanupClosure = lookup.findVirtual(directBufferPoolModuleClass, "liftedTree1$1$$anonfun$1", methodType)
    val directBuffer: ByteBuffer = ByteBuffer.allocateDirect(16)
    val cleanerMethod: Method = classOf[Object].getMethod("getClass")
    val cleanMethod: Method = classOf[Object].getMethod("toString")

    cleanupClosure.invokeWithArguments(directBufferPoolModule, cleanerMethod, cleanMethod, directBuffer)
  }
}
