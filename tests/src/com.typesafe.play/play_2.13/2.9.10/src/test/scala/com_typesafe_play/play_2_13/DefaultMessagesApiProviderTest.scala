/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_2_13

import java.io.File

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.Configuration
import play.api.Environment
import play.api.Mode
import play.api.http.HttpConfiguration
import play.api.i18n.DefaultLangs
import play.api.i18n.DefaultMessagesApiProvider
import play.api.i18n.Lang
import play.api.i18n.Langs
import play.api.i18n.MessagesApi

class DefaultMessagesApiProviderTest {
  @Test
  def loadsClasspathMessagesThroughConfiguredEnvironmentClassLoader(): Unit = {
    val environment: Environment = Environment(new File("."), getClass.getClassLoader, Mode.Test)
    val configuration: Configuration = Configuration.reference
    val langs: Langs = new DefaultLangs(Seq(Lang("en")))
    val httpConfiguration: HttpConfiguration = HttpConfiguration.fromConfiguration(configuration, environment)

    val messagesApi: MessagesApi = new DefaultMessagesApiProvider(
      environment,
      configuration,
      langs,
      httpConfiguration
    ).get

    assertThat(messagesApi("dynamic.access.coverage.message")(Lang("en"))).isEqualTo(
      "Loaded by DefaultMessagesApiProvider"
    )
  }
}
