/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_parsing_3

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class Pekko_parsing_3Test {
  @Test
  def logHelperDelegatesDebugEnabledToLoggingAdapter(): Unit = {
    val helper: PekkoParsingLogHelperHarness = new PekkoParsingLogHelperHarness(true, false, false)

    assertThat(helper.isDebugEnabledThroughHelper).isTrue
    assertThat(helper.isInfoEnabledThroughHelper).isFalse
    assertThat(helper.isWarningEnabledThroughHelper).isFalse
  }

  @Test
  def logHelperDelegatesInfoEnabledToLoggingAdapter(): Unit = {
    val helper: PekkoParsingLogHelperHarness = new PekkoParsingLogHelperHarness(false, true, false)

    assertThat(helper.isDebugEnabledThroughHelper).isFalse
    assertThat(helper.isInfoEnabledThroughHelper).isTrue
    assertThat(helper.isWarningEnabledThroughHelper).isFalse
  }

  @Test
  def logHelperDelegatesWarningEnabledToLoggingAdapter(): Unit = {
    val helper: PekkoParsingLogHelperHarness = new PekkoParsingLogHelperHarness(false, false, true)

    assertThat(helper.isDebugEnabledThroughHelper).isFalse
    assertThat(helper.isInfoEnabledThroughHelper).isFalse
    assertThat(helper.isWarningEnabledThroughHelper).isTrue
  }

  @Test
  def logHelperUsesEmptyPrefixByDefault(): Unit = {
    val helper: PekkoParsingLogHelperHarness = new PekkoParsingLogHelperHarness(false, false, false)

    assertThat(helper.prefixStringThroughHelper).isEmpty
  }
}
