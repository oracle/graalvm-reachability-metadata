/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_json_3

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.time.{Instant, LocalDate}
import java.util.UUID

import org.junit.jupiter.api.Assertions._
import org.junit.jupiter.api.Test
import play.api.libs.functional.syntax._
import play.api.libs.json._

case class PostalAddress(streetName: String, city: String, zipCode: String)

object PostalAddress:
  given OFormat[PostalAddress] = Json.format[PostalAddress]

case class CustomerProfile(
    customerId: UUID,
    displayName: String,
    birthDate: LocalDate,
    address: PostalAddress,
    aliases: Seq[String],
    marketingOptIn: Option[Boolean]
)

object CustomerProfile:
  given OFormat[CustomerProfile] = Json.format[CustomerProfile]

case class ApiToken(tokenId: String, expiresAt: Instant, refreshToken: Option[String])

object ApiToken:
  given JsonConfiguration = JsonConfiguration(JsonNaming.SnakeCase)
  given OFormat[ApiToken] = Json.format[ApiToken]

case class InventoryItem(sku: String, quantity: Int, tags: Seq[String])

object InventoryItem:
  private val nonBlankString: Reads[String] =
    Reads.StringReads.filter(JsonValidationError("error.expected.nonblank"))(_.trim.nonEmpty)
  private val positiveInt: Reads[Int] =
    Reads.IntReads.filter(JsonValidationError("error.expected.positive"))(_ > 0)

  given Reads[InventoryItem] = (
    (__ \ "sku").read[String](nonBlankString) and
      (__ \ "quantity").read[Int](positiveInt) and
      (__ \ "tags").readWithDefault[Seq[String]](Seq.empty[String])
  )(InventoryItem.apply _)

class Play_json_3Test {
  @Test
  def parsesNavigatesAndPrintsJsonValues(): Unit = {
    val rawJson: String =
      """
        |{
        |  "name": "Ada Lovelace",
        |  "active": true,
        |  "score": 99.50,
        |  "languages": ["scala", "java", "json"],
        |  "metadata": {"unicode": "café", "empty": null}
        |}
        |""".stripMargin

    val value: JsValue = Json.parse(rawJson)
    val obj: JsObject = value.as[JsObject]

    assertEquals("Ada Lovelace", (value \ "name").as[String])
    assertEquals(BigDecimal("99.50"), (value \ "score").as[BigDecimal])
    assertTrue((value \ "active").as[Boolean])
    assertEquals(Seq("scala", "java", "json"), (value \ "languages").as[Seq[String]])
    assertEquals(JsNull, (value \ "metadata" \ "empty").get)
    assertEquals(Set("name", "active", "score", "languages", "metadata"), obj.keys.toSet)

    val compact: String = Json.stringify(value)
    val pretty: String = Json.prettyPrint(value)
    val ascii: String = Json.asciiStringify(value)

    assertEquals(value, Json.parse(compact))
    assertEquals(value, Json.parse(pretty))
    assertEquals(value, Json.parse(ascii))
    assertTrue(ascii.contains("\\u00E9") || ascii.contains("\\u00e9"))
  }

  @Test
  def buildsMergesAndUpdatesJsonObjectsAndArrays(): Unit = {
    val base: JsObject = Json.obj(
      "customer" -> Json.obj(
        "id" -> "c-123",
        "preferences" -> Json.obj("theme" -> "light", "email" -> true)
      ),
      "tags" -> Json.arr("new")
    )
    val patch: JsObject = Json.obj(
      "customer" -> Json.obj(
        "preferences" -> Json.obj("theme" -> "dark"),
        "tier" -> "gold"
      ),
      "tags" -> Json.arr("returning")
    )

    val merged: JsObject = base.deepMerge(patch)
    assertEquals("c-123", (merged \ "customer" \ "id").as[String])
    assertEquals("dark", (merged \ "customer" \ "preferences" \ "theme").as[String])
    assertTrue((merged \ "customer" \ "preferences" \ "email").as[Boolean])
    assertEquals("gold", (merged \ "customer" \ "tier").as[String])
    assertEquals(Seq("returning"), (merged \ "tags").as[Seq[String]])

    val appended: JsArray = Json.arr("middle").prepend(JsString("first")).append(JsString("last"))
    assertEquals(Seq("first", "middle", "last"), appended.as[Seq[String]])

    val withoutTier: JsObject = merged - "tags"
    assertFalse((withoutTier \ "tags").isDefined)
  }

  @Test
  def roundTripsBytesAndInputStreams(): Unit = {
    val original: JsObject = Json.obj(
      "message" -> "hello",
      "count" -> 3,
      "nested" -> Json.obj("ok" -> true)
    )

    val bytes: Array[Byte] = Json.toBytes(original)
    assertEquals(original, Json.parse(bytes))
    assertEquals(original, Json.parse(new String(bytes, StandardCharsets.UTF_8)))

    val stream: ByteArrayInputStream = new ByteArrayInputStream(bytes)
    try assertEquals(original, Json.parse(stream))
    finally stream.close()
  }

  @Test
  def derivesFormatsForNestedDomainModels(): Unit = {
    val profile: CustomerProfile = CustomerProfile(
      customerId = UUID.fromString("00000000-0000-0000-0000-000000000123"),
      displayName = "Grace Hopper",
      birthDate = LocalDate.of(1906, 12, 9),
      address = PostalAddress("Arlington Boulevard", "Arlington", "22201"),
      aliases = Seq("Amazing Grace", "Compiler Pioneer"),
      marketingOptIn = Some(false)
    )

    val json: JsObject = Json.toJsObject(profile)
    assertEquals("00000000-0000-0000-0000-000000000123", (json \ "customerId").as[String])
    assertEquals("1906-12-09", (json \ "birthDate").as[String])
    assertEquals("Arlington", (json \ "address" \ "city").as[String])
    assertEquals(Seq("Amazing Grace", "Compiler Pioneer"), (json \ "aliases").as[Seq[String]])
    assertFalse((json \ "marketingOptIn").as[Boolean])
    assertEquals(profile, json.as[CustomerProfile])
  }

  @Test
  def honorsConfiguredSnakeCaseMacroFormatsAndMissingOptions(): Unit = {
    val token: ApiToken = ApiToken(
      tokenId = "token-42",
      expiresAt = Instant.parse("2030-01-02T03:04:05Z"),
      refreshToken = None
    )

    val json: JsObject = Json.toJsObject(token)
    assertEquals("token-42", (json \ "token_id").as[String])
    assertEquals("2030-01-02T03:04:05Z", (json \ "expires_at").as[String])
    assertFalse((json \ "refresh_token").isDefined)
    assertEquals(token, json.as[ApiToken])

    val withRefresh: JsValue = json + ("refresh_token" -> JsString("refresh-99"))
    assertEquals(Some("refresh-99"), withRefresh.as[ApiToken].refreshToken)
  }

  @Test
  def appliesCustomReadsWithDefaultsAndReportsAllValidationPaths(): Unit = {
    val valid: JsObject = Json.obj("sku" -> "BOOK-001", "quantity" -> 4)
    assertEquals(InventoryItem("BOOK-001", 4, Seq.empty[String]), valid.as[InventoryItem])

    val tagged: JsObject = Json.obj("sku" -> "PEN-002", "quantity" -> 12, "tags" -> Json.arr("office", "blue"))
    assertEquals(InventoryItem("PEN-002", 12, Seq("office", "blue")), tagged.as[InventoryItem])

    val invalid: JsObject = Json.obj("sku" -> "  ", "quantity" -> 0, "tags" -> Json.arr("bad"))
    invalid.validate[InventoryItem] match
      case JsError(errors) =>
        assertTrue(errors.exists { case (path, _) => path.toString.contains("sku") })
        assertTrue(errors.exists { case (path, _) => path.toString.contains("quantity") })
        val messages: List[String] = errors.flatMap { case (_, validationErrors) =>
          validationErrors.flatMap(_.messages)
        }.toList
        assertTrue(messages.contains("error.expected.nonblank"))
        assertTrue(messages.contains("error.expected.positive"))
      case success => fail(s"Expected validation errors, got $success")
  }

  @Test
  def transformsJsonBranchesWithPublicPathApi(): Unit = {
    val source: JsObject = Json.obj(
      "customer" -> Json.obj("id" -> "c-123", "name" -> "Ada"),
      "obsolete" -> Json.obj("remove" -> true),
      "audit" -> Json.obj("createdBy" -> "test")
    )

    val picked: JsObject = source.transform((__ \ "customer" \ "id").json.pickBranch).get
    assertEquals(Json.obj("customer" -> Json.obj("id" -> "c-123")), picked)

    val pruned: JsObject = source.transform((__ \ "obsolete").json.prune).get
    assertFalse((pruned \ "obsolete").isDefined)
    assertEquals("Ada", (pruned \ "customer" \ "name").as[String])

    val inserted: JsObject = source.transform((__ \ "customer" \ "status").json.put(JsString("active"))).get
    assertEquals(Json.obj("customer" -> Json.obj("status" -> "active")), inserted)
  }

  @Test
  def readsAndWritesCollectionsMapsTuplesAndTemporalValues(): Unit = {
    val instant: Instant = Instant.parse("2025-05-06T07:08:09Z")
    val localDate: LocalDate = LocalDate.of(2025, 5, 6)
    val payload: JsObject = Json.obj(
      "numbers" -> Seq(1, 2, 3),
      "flags" -> Set(true, false),
      "labels" -> Map("primary" -> "green", "secondary" -> "blue"),
      "pair" -> Json.obj("left" -> "age", "right" -> 42),
      "instant" -> instant,
      "localDate" -> localDate
    )

    assertEquals(Seq(1, 2, 3), (payload \ "numbers").as[Seq[Int]])
    assertEquals(Set(true, false), (payload \ "flags").as[Set[Boolean]])
    assertEquals(Map("primary" -> "green", "secondary" -> "blue"), (payload \ "labels").as[Map[String, String]])
    assertEquals(("age", 42), (payload \ "pair").as[(String, Int)](Reads.tuple2[String, Int]("left", "right")))
    assertEquals(instant, (payload \ "instant").as[Instant])
    assertEquals(localDate, (payload \ "localDate").as[LocalDate])
  }

  @Test
  def performsRecursiveLookupAndSafeMissingValueHandling(): Unit = {
    val document: JsObject = Json.obj(
      "id" -> "root",
      "items" -> Json.arr(
        Json.obj("id" -> "child-1", "details" -> Json.obj("id" -> "detail-1")),
        Json.obj("id" -> "child-2")
      )
    )

    val ids: List[String] = (document \\ "id").map(_.as[String]).toList
    assertEquals(4, ids.size)
    assertEquals(Set("root", "child-1", "detail-1", "child-2"), ids.toSet)

    val missing: JsLookupResult = document \ "doesNotExist"
    assertFalse(missing.isDefined)
    assertEquals(JsString("fallback"), missing.getOrElse(JsString("fallback")))
    assertEquals(None, missing.toOption)
    assertEquals(Some("root"), (document \ "id").validate[String].asOpt)
    assertEquals(None, (document \ "items" \ "missing").validateOpt[String].get)
  }
}
