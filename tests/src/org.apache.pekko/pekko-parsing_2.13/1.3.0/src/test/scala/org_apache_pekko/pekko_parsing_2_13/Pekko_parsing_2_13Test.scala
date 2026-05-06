/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_parsing_2_13

import org.apache.pekko.http.ccompat.{pre213macro, since213macro}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PekkoParsingCompatibilityAnnotationsTest {
  @Test
  def exposesMacroImplementationCompanions(): Unit = {
    assertThat(since213macro).isNotNull
    assertThat(pre213macro).isNotNull
  }
}
