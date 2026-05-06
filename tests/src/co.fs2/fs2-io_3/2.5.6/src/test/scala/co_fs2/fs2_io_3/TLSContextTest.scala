/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_fs2.fs2_io_3

import cats.effect.{Blocker, ContextShift, IO}
import fs2.io.tls.TLSContext
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

import scala.concurrent.ExecutionContext

class TLSContextTest {
  private implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  @Test
  def loadsTlsContextFromClasspathKeyStoreResource(): Unit = {
    val password: Array[Char] = "changeit".toCharArray
    val resourceName: String = "co_fs2/fs2_io_3/tls-context-empty.p12"

    val context: TLSContext = Blocker[IO].use { blocker =>
      TLSContext.fromKeyStoreResource[IO](resourceName, password, password, blocker)
    }.unsafeRunSync()

    assertNotNull(context)
  }
}
