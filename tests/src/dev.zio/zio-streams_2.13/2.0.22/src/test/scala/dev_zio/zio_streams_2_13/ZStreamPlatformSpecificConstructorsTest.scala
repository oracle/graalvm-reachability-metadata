/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_streams_2_13

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import zio.Runtime
import zio.Unsafe
import zio.ZIO
import zio.stream.ZStream

class ZStreamPlatformSpecificConstructorsTest {
  @Test
  def readsClasspathResourceThroughZStream(): Unit = {
    val header: Array[Byte] = unsafeRun(
      ZStream
        .fromResource("zio\\stream\\ZStream.class", chunkSize = 2)
        .take(4L)
        .runCollect
        .map(_.toArray)
    )

    assertArrayEquals(Array[Byte](0xca.toByte, 0xfe.toByte, 0xba.toByte, 0xbe.toByte), header)
  }

  private def unsafeRun[A](effect: ZIO[Any, Throwable, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
