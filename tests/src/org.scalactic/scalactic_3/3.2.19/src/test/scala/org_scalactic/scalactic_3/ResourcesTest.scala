/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalactic.scalactic_3

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.scalactic.Requirements

class ResourcesTest {
  @Test
  def failedRequirementLoadsLocalizedFailureMessage(): Unit = {
    val thrown: IllegalArgumentException = assertThrows(
      classOf[IllegalArgumentException],
      () => Requirements.require(false)
    )

    assertThat(thrown.getMessage).isNotBlank()
    assertThat(thrown.getMessage).contains("false")
  }
}
