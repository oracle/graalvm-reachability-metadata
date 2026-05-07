/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework_anorm.anorm_3

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import scala.runtime.LazyVals

class LazyValsTest {
  @Test
  def evaluatesMemberLazyValOnce(): Unit = {
    val holder: LazyValHolder = new LazyValHolder()

    assertThat(holder.evaluations).isEqualTo(0)
    assertThat(holder.value).isEqualTo("computed-1")
    assertThat(holder.value).isEqualTo("computed-1")
    assertThat(holder.evaluations).isEqualTo(1)
  }

  @Test
  def computesDeclaredFieldOffsetForLazyValStateField(): Unit = {
    val holder: LazyValOffsetHolder = new LazyValOffsetHolder()
    val stateOffset: Long = LazyVals.getOffset(classOf[LazyValOffsetHolder], "state")

    assertThat(stateOffset).isGreaterThanOrEqualTo(0L)
    assertThat(LazyVals.get(holder, stateOffset)).isEqualTo(0L)
  }
}

final class LazyValHolder {
  private var evaluationCount: Int = 0

  lazy val value: String = {
    evaluationCount += 1
    s"computed-$evaluationCount"
  }

  def evaluations: Int = evaluationCount
}

final class LazyValOffsetHolder {
  var state: Long = 0L
}
