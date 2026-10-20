/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_parser_3

import io.circe.{Json, ParsingFailure}
import io.circe.parser.decode
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue, fail}
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/** Exercises JSON string unescaping and Unicode decoding for a localized notification catalog. */
@Timeout(60)
class LocalizedMessageCatalogTest {
  @Test
  def loadsEscapedMessagesAndPreservesThemWhenCachingTheCatalog(): Unit = {
    val escape: String = "\\"
    val catalogJson: String =
      s"""{
         |  "welcome": "Bonjour, Zo${escape}u00eb!",
         |  "launch": "Commande exp${escape}u00e9di${escape}u00e9e ${escape}uD83D${escape}uDE80",
         |  "receipt": "Article: ${escape}"caf${escape}u00e9${escape}"${escape}nTotal:${escape}t12 EUR",
         |  "exportPath": "C:${escape}${escape}Exports${escape}${escape}receipt.json",
         |  "helpUrl": "https:${escape}/${escape}/shop.example.test${escape}/aide",
         |  "r${escape}u00e9sum${escape}u00e9": "2 articles"
         |}""".stripMargin

    val catalog: Map[String, String] =
      decode[Map[String, String]](catalogJson).fold(error => throw error, identity)

    val expected: Map[String, String] = Map(
      "welcome" -> "Bonjour, Zo\u00eb!",
      "launch" -> ("Commande exp\u00e9di\u00e9e " + new String(Character.toChars(0x1f680))),
      "receipt" -> "Article: \"caf\u00e9\"\nTotal:\t12 EUR",
      "exportPath" -> "C:\\Exports\\receipt.json",
      "helpUrl" -> "https://shop.example.test/aide",
      "r\u00e9sum\u00e9" -> "2 articles"
    )
    assertEquals(expected, catalog)

    val notification: String =
      List(catalog("welcome"), catalog("launch"), catalog("receipt")).mkString("\n\n")
    assertEquals(
      expected("welcome") + "\n\n" + expected("launch") + "\n\n" + expected("receipt"),
      notification
    )

    val cachedCatalog: String = Json.fromFields(
      catalog.toVector.sortBy(_._1).map { case (key, value) => key -> Json.fromString(value) }
    ).noSpaces
    assertEquals(Right(expected), decode[Map[String, String]](cachedCatalog))
  }

  @Test
  def rejectsBrokenEscapesRatherThanPublishingCorruptedMessages(): Unit = {
    val escape: String = "\\"
    val invalidCatalogs: List[String] = List(
      s"""{"welcome":"Bonjour${escape}q"}""",
      s"""{"welcome":"Bonjour ${escape}u00G9"}""",
      """{"welcome":"Bonjour
        |client"}""".stripMargin
    )

    invalidCatalogs.foreach { catalogJson =>
      decode[Map[String, String]](catalogJson) match {
        case Left(error) => assertTrue(error.isInstanceOf[ParsingFailure])
        case Right(_) => fail("An invalid message catalog must not be published")
      }
    }
  }
}
