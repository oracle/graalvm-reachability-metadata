/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalactic.scalactic_3

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.scalactic.source.ObjectMeta

import scala.annotation.targetName

class ObjectMetaAnonymous1Anonymous2Test {
  @Test
  def valueFallsBackToSpecializedAccessorName(): Unit = {
    val fixture: ObjectMetaSpecializedAccessorFixture = new ObjectMetaSpecializedAccessorFixture(42)
    val meta: ObjectMeta = ObjectMeta(fixture)

    assertThat(meta.value("score")).isEqualTo(42)
  }
}

final class ObjectMetaSpecializedAccessorFixture(scoreInput: Int) {
  private[this] val storedScore: Int = scoreInput

  @targetName("score$mcI$sp")
  private def specializedScore: Int = storedScore
}
