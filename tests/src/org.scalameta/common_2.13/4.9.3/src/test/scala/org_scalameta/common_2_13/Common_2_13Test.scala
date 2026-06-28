/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalameta.common_2_13

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.scalameta.logger

class Common_2_13Test {
  @Test
  def revealsWhitespaceCharacters(): Unit = {
    val rendered: String = logger.revealWhitespace("a b\t\n")

    assertEquals("a\u2219b\u2020\u00b6", rendered)
  }
}
