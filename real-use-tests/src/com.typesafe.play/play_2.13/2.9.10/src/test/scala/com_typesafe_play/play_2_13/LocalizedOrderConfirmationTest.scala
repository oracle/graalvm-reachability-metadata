/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_2_13

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import play.api.i18n.{DefaultLangs, DefaultMessagesApi, Lang, Messages, MessagesApi}

/** Exercises Play internationalization for order-confirmation emails in a customer's preferred language. */
@Timeout(60)
class LocalizedOrderConfirmationTest {
  case class Confirmation(subject: String, body: String)

  private def confirmation(customer: String, order: String, quantity: Int)(implicit messages: Messages): Confirmation = {
    Confirmation(
      messages("order.subject", order),
      Seq(
        messages("order.greeting", customer),
        messages("order.items", quantity),
        messages("order.support", "support@example.com")
      ).mkString("\n")
    )
  }

  @Test
  def selectsTranslationsAndFallsBackForUnsupportedLanguages(): Unit = {
    val catalog: Map[String, Map[String, String]] = Map(
      "default" -> Map(
        "order.support" -> "Support: {0}"
      ),
      "en" -> Map(
        "order.subject" -> "Order {0} confirmed",
        "order.greeting" -> "Hello {0},",
        "order.items" -> "{0,choice,0#No items|1#One item|1<{0,number,integer} items}"
      ),
      "fr" -> Map(
        "order.subject" -> "Commande {0} confirmee",
        "order.greeting" -> "Bonjour {0},",
        "order.items" -> "{0,choice,0#Aucun article|1#Un article|1<{0,number,integer} articles}"
      )
    )
    val api: MessagesApi = new DefaultMessagesApi(
      messages = catalog,
      langs = new DefaultLangs(Seq(Lang("en"), Lang("fr")))
    )

    // A regional preference can use the application's general French translations.
    val french: Messages = api.preferred(Seq(Lang("fr-CA"), Lang("en")))
    assertEquals("fr", french.lang.code)
    val frenchMail: Confirmation = confirmation("Camille", "ORD-104", 3)(french)
    assertEquals("Commande ORD-104 confirmee", frenchMail.subject)
    assertEquals("Bonjour Camille,\n3 articles\nSupport: support@example.com", frenchMail.body)

    // Try the next customer preference before falling back to the application's default.
    val secondPreference: Messages = api.preferred(Seq(Lang("de"), Lang("fr")))
    assertEquals("fr", secondPreference.lang.code)
    assertEquals(
      "Bonjour Camille,\nUn article\nSupport: support@example.com",
      confirmation("Camille", "ORD-105", 1)(secondPreference).body
    )

    val fallback: Messages = api.preferred(Seq(Lang("de")))
    assertEquals("en", fallback.lang.code)
    val englishMail: Confirmation = confirmation("Ada", "ORD-106", 1)(fallback)
    assertEquals("Order ORD-106 confirmed", englishMail.subject)
    assertEquals("Hello Ada,\nOne item\nSupport: support@example.com", englishMail.body)
    assertEquals(
      "Hello Ada,\nNo items\nSupport: support@example.com",
      confirmation("Ada", "ORD-107", 0)(api.preferred(Seq.empty)).body
    )
  }
}
