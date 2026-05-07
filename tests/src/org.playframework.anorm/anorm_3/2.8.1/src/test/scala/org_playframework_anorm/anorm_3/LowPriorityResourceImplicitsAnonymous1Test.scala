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

class LowPriorityResourceImplicitsAnonymous1Test {
  @Test
  def closesStructuralCloseableThroughReflectiveResource(): Unit = {
    val target: ReflectiveCloseTarget = new ReflectiveCloseTarget()
    val managedResource: Resource[ReflectiveCloseTarget] =
      Resource.reflectiveCloseableResource[ReflectiveCloseTarget]

    managedResource.close(target)

    assertThat(target.closed).isTrue()
  }
}

class ReflectiveCloseTarget {
  var closed: Boolean = false

  def close(): Unit = {
    closed = true
  }
}
