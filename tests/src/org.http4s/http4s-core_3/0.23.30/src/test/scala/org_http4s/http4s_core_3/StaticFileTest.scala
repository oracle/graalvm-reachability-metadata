/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_core_3

import cats.effect.SyncIO
import org.http4s.StaticFile
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StaticFileTest {
  @Test
  def fromResourceLooksUpClasspathResource(): Unit = {
    val response = StaticFile
      .fromResource[SyncIO]("missing-static-file-resource.txt")
      .value
      .unsafeRunSync()

    assertTrue(response.isEmpty)
  }
}
