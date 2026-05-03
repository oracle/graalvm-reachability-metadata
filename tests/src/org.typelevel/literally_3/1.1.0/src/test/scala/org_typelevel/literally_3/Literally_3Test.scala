/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.literally_3

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

final class Literally_3Test {
  import LiterallyTestFixtures.*

  @Test
  def literalsGenerateDomainValuesAtCompileTime(): Unit = {
    val ephemeralPort = port"49152"
    val maximumPort = port"65535"
    val color = rgb"#0a10FF"

    assertThat(ephemeralPort.value).isEqualTo(49152)
    assertThat(maximumPort.value).isEqualTo(65535)
    assertThat(color.red).isEqualTo(10)
    assertThat(color.green).isEqualTo(16)
    assertThat(color.blue).isEqualTo(255)
  }

  @Test
  def validatorReceivesExactlyOneStringContextPart(): Unit = {
    val label = label"service-A_42-blue_green"

    assertThat(label.value).isEqualTo("service-A_42-blue_green")
  }

  @Test
  def boundaryPortLiteralsGenerateExpectedValues(): Unit = {
    val minimumPort = port"0"
    val privilegedPort = port"443"
    val maximumPort = port"65535"

    assertThat(minimumPort.value).isZero()
    assertThat(privilegedPort.value).isEqualTo(443)
    assertThat(maximumPort.value).isEqualTo(65535)
  }

  @Test
  def rgbLiteralSupportsLowercaseAndUppercaseHexDigits(): Unit = {
    val black = rgb"#000000"
    val mixedCase = rgb"#aBcD09"
    val white = rgb"#FFFFFF"

    assertThat(black).isEqualTo(RgbColor(0, 0, 0))
    assertThat(mixedCase).isEqualTo(RgbColor(171, 205, 9))
    assertThat(white).isEqualTo(RgbColor(255, 255, 255))
  }
}

