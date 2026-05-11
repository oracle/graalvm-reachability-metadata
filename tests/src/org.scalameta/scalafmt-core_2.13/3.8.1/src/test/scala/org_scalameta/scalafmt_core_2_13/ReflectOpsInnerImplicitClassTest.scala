/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalameta.scalafmt_core_2_13

import metaconfig.Configured
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.scalafmt.Scalafmt
import org.scalafmt.config.ScalafmtConfig

class ReflectOpsInnerImplicitClassTest {

  @Test
  def decodesDialectOverrideThroughPublicConfigApi(): Unit = {
    val config: ScalafmtConfig = decodeConfig(
      """
        |runner.dialect = scala213
        |runner.dialectOverride.allowTrailingCommas = false
        |""".stripMargin
    )

    val formatted = Scalafmt.format(
      """
        |object Example {
        |  val values = List(
        |    1,
        |  )
        |}
        |""".stripMargin,
      config
    )

    assertTrue(
      formatted.toEither.left.toOption.isDefined,
      "overridden dialect rejects a trailing comma"
    )
  }

  private def decodeConfig(configText: String): ScalafmtConfig = {
    ScalafmtConfig.fromHoconString(configText) match {
      case Configured.Ok(config) => config
      case error =>
        throw new AssertionError(
          s"Expected scalafmt config to decode successfully, got: $error"
        )
    }
  }
}
