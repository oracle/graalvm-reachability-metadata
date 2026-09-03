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

class LowPriorityResourceImplicitsAnonymous2Test {
  @Test
  def reflectiveDisposableResourceInvokesPublicDisposeMethod(): Unit = {
    val handle: DisposeTrackingHandle = new DisposeTrackingHandle
    val disposable: Resource[DisposeTrackingHandle] =
      Resource.reflectiveDisposableResource[DisposeTrackingHandle]

    assertFalse(handle.disposed)

    disposable.close(handle)

    assertTrue(handle.disposed)
  }
}

final class DisposeTrackingHandle {
  private var disposedFlag: Boolean = false

  def dispose(): Unit = {
    disposedFlag = true
  }

  def disposed: Boolean = disposedFlag
}
