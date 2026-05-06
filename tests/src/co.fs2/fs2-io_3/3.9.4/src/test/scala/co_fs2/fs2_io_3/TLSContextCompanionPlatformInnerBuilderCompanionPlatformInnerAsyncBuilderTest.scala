/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_fs2.fs2_io_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.io.net.tls.TLSContext
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class TLSContextCompanionPlatformInnerBuilderCompanionPlatformInnerAsyncBuilderTest {
  private val keyStoreResource: String = "co_fs2/fs2_io_3/tls-test-keystore.p12"
  private val password: Array[Char] = "changeit".toCharArray

  @Test
  def fromKeyStoreResourceLoadsTlsContextFromClassLoaderResource(): Unit = {
    val tlsContext: TLSContext[IO] = TLSContext.Builder
      .forAsync[IO]
      .fromKeyStoreResource(keyStoreResource, password, password)
      .unsafeRunSync()

    assertNotNull(tlsContext)
  }
}
