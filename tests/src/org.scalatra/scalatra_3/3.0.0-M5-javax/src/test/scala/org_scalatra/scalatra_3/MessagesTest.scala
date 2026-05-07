/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatra.scalatra_3

import java.util.Locale

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.scalatra.i18n.Messages

class MessagesTest {
  @Test
  def loadsMessagesFromResourceBundleForLocale(): Unit = {
    val messages: Messages = Messages(Locale.ENGLISH, "i18n/messages")

    assertThat(messages.get("greeting").contains("Hello from Scalatra")).isTrue
    assertThat(messages("greeting")).isEqualTo("Hello from Scalatra")
    assertThat(messages.get("missing.key").isEmpty).isTrue
    assertThat(messages.getOrElse("missing.key", "fallback")).isEqualTo("fallback")
    assertThat(messages("missing.key", "default greeting")).isEqualTo("default greeting")
  }
}
