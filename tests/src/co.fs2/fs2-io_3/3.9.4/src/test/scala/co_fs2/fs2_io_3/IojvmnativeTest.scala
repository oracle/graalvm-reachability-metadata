/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_fs2.fs2_io_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.io
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

import java.nio.charset.StandardCharsets

class IojvmnativeTest {
  private val resourceName: String = "iojvmnative-resource.txt"
  private val packageResourceName: String = s"co_fs2/fs2_io_3/$resourceName"
  private val expectedResourceText: String = "fs2 resource stream from test resources\n"

  @Test
  def readClassResourceReadsResourceRelativeToClass(): Unit = {
    val actualText: String = io
      .readClassResource[IO, IojvmnativeTest](resourceName, chunkSize = 7)
      .compile
      .toVector
      .map(bytes => new String(bytes.toArray, StandardCharsets.UTF_8))
      .unsafeRunSync()

    assertEquals(expectedResourceText, actualText)
  }

  @Test
  def readClassLoaderResourceReadsResourceFromClassLoader(): Unit = {
    val actualText: String = io
      .readClassLoaderResource[IO](packageResourceName, chunkSize = 5)
      .compile
      .toVector
      .map(bytes => new String(bytes.toArray, StandardCharsets.UTF_8))
      .unsafeRunSync()

    assertEquals(expectedResourceText, actualText)
  }
}
