/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_core_3

import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.Headers
import org.http4s.LanguageTag
import org.http4s.QValue
import org.http4s.Request
import org.http4s.Response
import org.http4s.Status
import org.http4s.headers.`Accept-Language`
import org.http4s.headers.`Content-Language`
import org.http4s.syntax.literals.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.typelevel.ci.*

/**
 * Negotiates the response language with `Accept-Language`, `LanguageTag`, and `Content-Language`.
 *
 * A help center keeps each article in a handful of translations. It reads the reader's ranked
 * language preferences, serves the translation the reader rates highest, labels the body with
 * `Content-Language`, and answers `406 Not Acceptable` when every translation is refused.
 */
class HelpCenterLocalizationTest {

  @Test
  def parsesRankedLanguagePreferencesFromABrowser(): Unit = {
    val request: Request[IO] = Request[IO](
      uri = uri"/help/articles/reset-password",
      headers = Headers("Accept-Language" -> "de-CH, de;q=0.9, en-US;q=0.7, *;q=0.1")
    )
    val preferences: `Accept-Language` =
      request.headers.get[`Accept-Language`].getOrElse(throw new AssertionError("no Accept-Language"))

    val tags: List[LanguageTag] = preferences.values.toList
    assertEquals(List("de", "de", "en", "*"), tags.map(_.primaryTag))
    assertEquals(List(List("CH"), Nil, List("US"), Nil), tags.map(_.subTags))
    assertEquals(QValue.One, tags.head.q)
    assertEquals(quality("0.7"), tags(2).q)

    assertEquals(QValue.One, preferences.qValue(LanguageTag("de", QValue.One, List("CH"))))
    assertEquals(quality("0.9"), preferences.qValue(LanguageTag("de", QValue.One, List("AT"))))
    assertEquals(quality("0.7"), preferences.qValue(LanguageTag("en", QValue.One, List("US"))))
    assertEquals(quality("0.1"), preferences.qValue(LanguageTag("fr")))
    assertTrue(preferences.satisfiedBy(LanguageTag("fr")))
  }

  @Test
  def servesTheTranslationTheReaderRatesHighest(): Unit = {
    val swissReader: Request[IO] = Request[IO](uri = uri"/help/articles/reset-password")
      .putHeaders(
        `Accept-Language`(
          LanguageTag("fr", QValue.One, List("CH")),
          LanguageTag("de").withQValue(quality("0.8")),
          LanguageTag("en").withQValue(quality("0.5"))
        )
      )
    val german: Response[IO] = serve(swissReader)
    assertEquals(Status.Ok, german.status)
    assertEquals(List("de"), contentLanguages(german))
    assertEquals("Passwort zuruecksetzen", german.as[String].unsafeRunSync())
    assertEquals(Some("Accept-Language"), german.headers.get(ci"Vary").map(_.head.value))

    val canadianReader: Request[IO] = Request[IO](
      uri = uri"/help/articles/reset-password",
      headers = Headers("Accept-Language" -> "fr-ca;q=0.9, en-GB;q=0.9, en;q=0.4")
    )
    val canadian: Response[IO] = serve(canadianReader)
    assertEquals(List("en-GB"), contentLanguages(canadian))
    assertEquals("Reset your password", canadian.as[String].unsafeRunSync())

    val noPreference: Response[IO] = serve(Request[IO](uri = uri"/help/articles/reset-password"))
    assertEquals(List("en-GB"), contentLanguages(noPreference))
  }

  @Test
  def refusesWhenTheReaderExcludesEveryTranslation(): Unit = {
    val japaneseOnly: Request[IO] = Request[IO](
      uri = uri"/help/articles/reset-password",
      headers = Headers("Accept-Language" -> "ja, *;q=0")
    )
    val preferences: `Accept-Language` =
      japaneseOnly.headers.get[`Accept-Language`].getOrElse(throw new AssertionError("no header"))
    assertFalse(preferences.satisfiedBy(LanguageTag("en", QValue.One, List("GB"))))
    assertTrue(preferences.satisfiedBy(LanguageTag("ja")))

    val refused: Response[IO] = serve(japaneseOnly)
    assertEquals(Status.NotAcceptable, refused.status)
    assertTrue(refused.headers.get[`Content-Language`].isEmpty)
    assertEquals("Available languages: en-GB, de, fr-FR", refused.as[String].unsafeRunSync())

    assertTrue(`Accept-Language`.parse("en;q=1.5").isLeft)
  }

  @Test
  def rendersTypedLanguageHeadersForOutgoingMessages(): Unit = {
    val outgoing: `Accept-Language` = `Accept-Language`(
      LanguageTag("pt", QValue.One, List("BR")),
      LanguageTag("pt").withQValue(quality("0.8")),
      LanguageTag.`*`.withQValue(quality("0.2"))
    )
    val request: Request[IO] = Request[IO](uri = uri"/help").putHeaders(outgoing)
    assertEquals(
      Some("pt-BR, pt;q=0.8, *;q=0.2"),
      request.headers.get(ci"Accept-Language").map(_.head.value)
    )

    val bilingual: `Content-Language` = `Content-Language`.parse("en-GB, cy").fold(throw _, identity)
    assertEquals(NonEmptyList.of("en-GB", "cy"), bilingual.values.map(_.renderString))
    val response: Response[IO] = Response[IO](Status.Ok).putHeaders(bilingual)
    assertEquals(Some("en-GB, cy"), response.headers.get(ci"Content-Language").map(_.head.value))
  }

  /** One article translation the help center can serve. */
  final case class Translation(language: LanguageTag, title: String)

  /** The translations of the article, in the order the editors prefer them. */
  val Translations: List[Translation] = List(
    Translation(LanguageTag("en", QValue.One, List("GB")), "Reset your password"),
    Translation(LanguageTag("de"), "Passwort zuruecksetzen"),
    Translation(LanguageTag("fr", QValue.One, List("FR")), "Reinitialiser votre mot de passe")
  )

  /** Parses a quality value the way it appears in a header parameter. */
  def quality(value: String): QValue =
    QValue.fromString(value).fold(failure => throw failure, identity)

  /** Serves the article in the translation the reader's `Accept-Language` ranks highest. */
  def serve(request: Request[IO]): Response[IO] = {
    val chosen: Option[Translation] = request.headers.get[`Accept-Language`] match {
      case None => Translations.headOption
      case Some(preferences) =>
        val acceptable: List[(Translation, QValue)] = Translations
          .map(translation => (translation, preferences.qValue(translation.language)))
          .filter(_._2.isAcceptable)
        if (acceptable.isEmpty) None
        else Some(acceptable.maxBy(_._2)._1)
    }
    chosen match {
      case Some(translation) =>
        Response[IO](Status.Ok)
          .withEntity(translation.title)
          .putHeaders(`Content-Language`(translation.language), "Vary" -> "Accept-Language")
      case None =>
        val available: String = Translations.map(_.language.renderString).mkString(", ")
        Response[IO](Status.NotAcceptable).withEntity(s"Available languages: $available")
    }
  }

  /** Reads the languages a response declares for its body. */
  def contentLanguages(response: Response[IO]): List[String] =
    response.headers.get[`Content-Language`].toList.flatMap(_.values.toList.map(_.renderString))
}
