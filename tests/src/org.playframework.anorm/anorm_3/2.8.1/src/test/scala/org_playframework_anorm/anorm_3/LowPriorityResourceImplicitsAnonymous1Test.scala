/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework_anorm.anorm_3

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import resource.Resource

class LowPriorityResourceImplicitsAnonymous1Test {
  @Test
  def reflectiveCloseableResourceInvokesPublicCloseMethod(): Unit = {
    val handle: CloseTrackingHandle = new CloseTrackingHandle
    val closeable: Resource[CloseTrackingHandle] =
      Resource.reflectiveCloseableResource[CloseTrackingHandle]

    assertFalse(handle.closed)

    closeable.close(handle)

    assertTrue(handle.closed)
  }
}

final class CloseTrackingHandle {
  private var closedFlag: Boolean = false

  def close(): Unit = {
    closedFlag = true
  }

  def closed: Boolean = closedFlag
}
