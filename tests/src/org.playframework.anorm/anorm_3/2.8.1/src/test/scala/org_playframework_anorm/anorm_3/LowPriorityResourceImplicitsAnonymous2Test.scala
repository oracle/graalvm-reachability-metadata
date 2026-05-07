/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework_anorm.anorm_3

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import resource.Resource

class LowPriorityResourceImplicitsAnonymous2Test {
  @Test
  def closesStructuralDisposableThroughReflectiveResource(): Unit = {
    val target: ReflectiveDisposeTarget = new ReflectiveDisposeTarget()
    val managedResource: Resource[ReflectiveDisposeTarget] =
      Resource.reflectiveDisposableResource[ReflectiveDisposeTarget]

    managedResource.close(target)

    assertThat(target.disposed).isTrue()
  }
}

class ReflectiveDisposeTarget {
  var disposed: Boolean = false

  def dispose(): Unit = {
    disposed = true
  }
}
