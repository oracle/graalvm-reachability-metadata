/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_3

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
import play.api.i18n.MessagesApi

class DefaultMessagesApiProviderTest {
  @Test
  def loadsMessagesFromApplicationResources(): Unit = {
    val environment: Environment = Environment(new File("."), getClass.getClassLoader, Mode.Test)
    val configuration: Configuration = Configuration.from(
      Map[String, Any](
        "play.i18n.langs" -> Seq("en"),
        "play.i18n.path" -> null,
        "play.i18n.langCookieName" -> "PLAY_LANG",
        "play.i18n.langCookieSecure" -> false,
        "play.i18n.langCookieHttpOnly" -> false,
        "play.i18n.langCookieSameSite" -> "lax",
        "play.i18n.langCookieMaxAge" -> null
      )
    )
    val provider: DefaultMessagesApiProvider = new DefaultMessagesApiProvider(
      environment,
      configuration,
      new DefaultLangs(Seq(Lang("en"))),
      HttpConfiguration()
    )

    val messagesApi: MessagesApi = provider.get

    assertThat(messagesApi("provider.greeting", "Play")(Lang("en"))).isEqualTo("Hello, Play!")
  }
}
