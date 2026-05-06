/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_json_2_13

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import play.api.libs.functional.syntax._
import play.api.libs.json.Format
import play.api.libs.json.JsArray
import play.api.libs.json.JsDefined
import play.api.libs.json.JsError
import play.api.libs.json.JsLookupResult
import play.api.libs.json.JsNull
import play.api.libs.json.JsNumber
import play.api.libs.json.JsObject
import play.api.libs.json.JsResultException
import play.api.libs.json.JsString
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.JsonConfiguration
import play.api.libs.json.JsonNaming
import play.api.libs.json.KeyReads
import play.api.libs.json.KeyWrites
import play.api.libs.json.OFormat
import play.api.libs.json.OWrites
import play.api.libs.json.Reads
import play.api.libs.json.Writes
import play.api.libs.json.__

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

object PlayJsonModels {
  final case class Address(street: String, city: String, postalCode: String)
  final case class Profile(name: String, age: Int, address: Address, tags: Seq[String], newsletter: Option[Boolean])
  final case class Payment(amount: BigDecimal, currency: String, metadata: Map[String, String])
  final case class SnakeProfile(firstName: String, lastName: String, postalCode: String)
  final case class WarehouseId(value: Int)
  final case class Inventory(counts: Map[WarehouseId, Int])

  object Priority extends Enumeration {
    val Low: Value = Value("low")
    val Normal: Value = Value("normal")
    val High: Value = Value("high")
  }
}

class Play_json_2_13Test {
  import PlayJsonModels._

  implicit val addressFormat: OFormat[Address] = Json.format[Address]
  implicit val profileFormat: OFormat[Profile] = Json.format[Profile]
  implicit val priorityFormat: Format[Priority.Value] = Json.formatEnum(Priority)

  @Test
  def parsesJsonFromStringsBytesAndInputStreamsAndRendersItBack(): Unit = {
    val rawJson: String =
      """{"name":"play-json","unicode":"Grüße ☃","numbers":[1,2.5,-3],"enabled":true,"empty":null}"""
    val expected: JsValue = Json.parse(rawJson)
    val bytes: Array[Byte] = rawJson.getBytes(StandardCharsets.UTF_8)

    val parsedValues: Seq[JsValue] = Seq(
      Json.parse(rawJson),
      Json.parse(bytes),
      Json.parse(new ByteArrayInputStream(bytes))
    )

    parsedValues.foreach { value =>
      assertEquals(expected, value)
      assertEquals("play-json", (value \ "name").as[String])
      assertEquals("Grüße ☃", (value \ "unicode").as[String])
      assertEquals(Seq(BigDecimal(1), BigDecimal("2.5"), BigDecimal(-3)), (value \ "numbers").as[Seq[BigDecimal]])
      assertTrue((value \ "enabled").as[Boolean])
      assertEquals(JsDefined(JsNull), value \ "empty")
    }

    val compact: String = Json.stringify(expected)
    val ascii: String = Json.asciiStringify(expected)
    val pretty: String = Json.prettyPrint(expected)

    assertEquals(expected, Json.parse(compact))
    assertEquals(expected, Json.parse(ascii))
    assertEquals(expected, Json.parse(Json.toBytes(expected)))
    assertEquals(expected, Json.parse(pretty))
    assertTrue(ascii.contains("\\u2603"))
    assertTrue(pretty.contains(System.lineSeparator()) || pretty.contains("\n"))
  }

  @Test
  def supportsJsonObjectArrayConstructionLookupAndMutationStyleOperations(): Unit = {
    val document: JsObject = Json.obj(
      "id" -> 7,
      "name" -> "document",
      "nested" -> Json.obj(
        "id" -> 8,
        "enabled" -> true,
        "items" -> Json.arr(
          Json.obj("kind" -> "book", "price" -> 12.50),
          Json.obj("kind" -> "pen", "price" -> 1.25)
        )
      )
    )

    assertEquals(7, (document \ "id").as[Int])
    assertEquals(8, (document \ "nested" \ "id").as[Int])
    assertEquals("pen", (document \ "nested" \ "items" \ 1 \ "kind").as[String])
    assertEquals(Seq(JsNumber(7), JsNumber(8)), document \\ "id")

    val missing: JsLookupResult = document \ "missing"
    assertFalse(missing.toOption.isDefined)
    assertEquals(JsDefined(JsString("document")), document \ "name")

    val changed: JsObject = document + ("status" -> JsString("indexed")) - "name" ++ Json.obj("extra" -> 42)
    assertEquals("indexed", (changed \ "status").as[String])
    assertEquals(42, (changed \ "extra").as[Int])
    assertFalse((changed \ "name").toOption.isDefined)
    assertEquals(2, (changed \ "nested" \ "items").as[JsArray].value.size)
  }

  @Test
  def roundTripsCaseClassesWithMacroFormatsAndSnakeCaseConfiguration(): Unit = {
    val profile: Profile = Profile(
      name = "Alice",
      age = 35,
      address = Address("Main Street", "London", "SW1A"),
      tags = Seq("admin", "editor"),
      newsletter = Some(true)
    )

    val json: JsValue = Json.toJson(profile)
    assertEquals("Alice", (json \ "name").as[String])
    assertEquals("London", (json \ "address" \ "city").as[String])
    assertEquals(Seq("admin", "editor"), (json \ "tags").as[Seq[String]])
    assertEquals(profile, json.validate[Profile].get)

    val withMissingOption: JsValue = Json.obj(
      "name" -> "Bob",
      "age" -> 29,
      "address" -> Json.obj("street" -> "High Street", "city" -> "Oxford", "postalCode" -> "OX1"),
      "tags" -> Json.arr("reader")
    )
    assertEquals(None, withMissingOption.as[Profile].newsletter)

    implicit val snakeConfig: JsonConfiguration = JsonConfiguration(JsonNaming.SnakeCase)
    implicit val snakeProfileFormat: OFormat[SnakeProfile] = Json.format[SnakeProfile]
    val snakeProfile: SnakeProfile = SnakeProfile("Ada", "Lovelace", "W1")
    val snakeJson: JsValue = Json.toJson(snakeProfile)

    assertEquals("Ada", (snakeJson \ "first_name").as[String])
    assertEquals("Lovelace", (snakeJson \ "last_name").as[String])
    assertEquals("W1", (snakeJson \ "postal_code").as[String])
    assertEquals(snakeProfile, snakeJson.validate[SnakeProfile].get)
  }

  @Test
  def appliesReadsWritesCombinatorsValidationConstraintsAndDefaults(): Unit = {
    implicit val paymentReads: Reads[Payment] = (
      (__ \ "amount").read[BigDecimal](Reads.min(BigDecimal(0))) and
        (__ \ "currency").read[String](Reads.pattern("^[A-Z]{3}$".r)) and
        (__ \ "metadata").readWithDefault[Map[String, String]](Map.empty)
    )(Payment.apply _)
    implicit val paymentWrites: OWrites[Payment] = (
      (__ \ "amount").write[BigDecimal] and
        (__ \ "currency").write[String] and
        (__ \ "metadata").write[Map[String, String]]
    )(payment => (payment.amount, payment.currency, payment.metadata))

    val input: JsValue = Json.obj("amount" -> BigDecimal("19.95"), "currency" -> "EUR")
    val payment: Payment = input.validate[Payment].get
    assertEquals(Payment(BigDecimal("19.95"), "EUR", Map.empty), payment)

    val serialized: JsObject = Json.toJsObject(payment.copy(metadata = Map("order" -> "A-1")))
    assertEquals(BigDecimal("19.95"), (serialized \ "amount").as[BigDecimal])
    assertEquals("A-1", (serialized \ "metadata" \ "order").as[String])

    val invalid: JsValue = Json.obj("amount" -> -1, "currency" -> "eur")
    val errorJson: JsValue = invalid.validate[Payment] match {
      case JsError(errors) => JsError.toJson(errors)
      case JsSuccess(value, _) => throw new AssertionError(s"Expected validation errors, got $value")
    }
    val errorJsonText: String = errorJson.toString()
    assertTrue(errorJsonText.contains("error.min"))
    assertTrue(errorJsonText.contains("error.pattern"))

    val exception: JsResultException = assertThrows(
      classOf[JsResultException],
      () => {
        invalid.as[Payment]
        ()
      }
    )
    assertTrue(exception.errors.nonEmpty)
  }

  @Test
  def transformsJsonDocumentsWithJsPathReads(): Unit = {
    val source: JsObject = Json.obj(
      "user" -> Json.obj(
        "name" -> "Grace Hopper",
        "password" -> "secret",
        "roles" -> Json.arr("admin", "operator")
      ),
      "enabled" -> true
    )

    val pruned: JsObject = source.transform((__ \ "user" \ "password").json.prune).get
    assertEquals("Grace Hopper", (pruned \ "user" \ "name").as[String])
    assertFalse((pruned \ "user" \ "password").toOption.isDefined)
    assertEquals(Seq("admin", "operator"), (pruned \ "user" \ "roles").as[Seq[String]])
    assertTrue((pruned \ "enabled").as[Boolean])

    val copied: JsObject = source.transform((__ \ "user" \ "fullName").json.copyFrom((__ \ "user" \ "name").json.pick)).get
    assertEquals("Grace Hopper", (copied \ "user" \ "fullName").as[String])
  }

  @Test
  def supportsPrimitiveCollectionsMapsOptionsEnumsAndJsResultHelpers(): Unit = {
    val value: JsValue = Json.obj(
      "ints" -> Seq(1, 2, 3),
      "flags" -> Set(true, false),
      "mapping" -> Map("one" -> 1, "two" -> 2),
      "maybe" -> Option("present"),
      "empty" -> JsNull,
      "priority" -> Priority.High
    )

    assertEquals(Seq(1, 2, 3), (value \ "ints").as[Seq[Int]])
    assertEquals(Set(true, false), (value \ "flags").as[Set[Boolean]])
    assertEquals(Map("one" -> 1, "two" -> 2), (value \ "mapping").as[Map[String, Int]])
    assertEquals(Some("present"), (value \ "maybe").asOpt[String])
    assertEquals(None, (value \ "empty").asOpt[String])
    assertEquals(Priority.High, (value \ "priority").as[Priority.Value])

    val success: String = (value \ "priority").validate[Priority.Value].fold(
      errors => throw new AssertionError(s"Unexpected errors: $errors"),
      priority => priority.toString
    )
    assertEquals("high", success)

    val failureMessage: String = JsString("urgent").validate[Priority.Value].fold(
      errors => JsError.toJson(errors).toString(),
      priority => throw new AssertionError(s"Unexpected priority: $priority")
    )
    assertTrue(failureMessage.contains("error"))
  }

  @Test
  def readsAndWritesMapsWithCustomKeyCodecs(): Unit = {
    implicit val warehouseIdKeyWrites: KeyWrites[WarehouseId] = KeyWrites { id =>
      s"warehouse-${id.value}"
    }
    implicit val warehouseIdKeyReads: KeyReads[WarehouseId] = KeyReads { key =>
      val prefix: String = "warehouse-"
      val number: String = key.stripPrefix(prefix)

      if (key.startsWith(prefix) && number.forall(_.isDigit) && number.nonEmpty) {
        JsSuccess(WarehouseId(number.toInt))
      } else {
        JsError("error.expected.warehouseId")
      }
    }
    implicit val inventoryFormat: OFormat[Inventory] = Json.format[Inventory]

    val inventory: Inventory = Inventory(Map(WarehouseId(10) -> 7, WarehouseId(20) -> 0))
    val json: JsValue = Json.toJson(inventory)

    assertEquals(7, (json \ "counts" \ "warehouse-10").as[Int])
    assertEquals(0, (json \ "counts" \ "warehouse-20").as[Int])
    assertEquals(inventory, json.validate[Inventory].get)

    val invalid: JsValue = Json.obj("counts" -> Json.obj("aisle-10" -> 2))
    val failureMessage: String = invalid.validate[Inventory].fold(
      errors => JsError.toJson(errors).toString(),
      parsed => throw new AssertionError(s"Unexpected inventory: $parsed")
    )
    assertTrue(failureMessage.contains("error.expected.warehouseId"))
  }

  @Test
  def roundTripsJavaTimeAndUuidValuesWithDefaultFormats(): Unit = {
    val instant: Instant = Instant.parse("2024-05-06T10:15:30.123Z")
    val offsetDateTime: OffsetDateTime = OffsetDateTime.parse("2024-05-06T10:15:30+02:00")
    val localDate: LocalDate = LocalDate.parse("2024-05-06")
    val uuid: UUID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")

    val json: JsValue = Json.obj(
      "instant" -> instant,
      "offsetDateTime" -> offsetDateTime,
      "localDate" -> localDate,
      "uuid" -> uuid
    )

    assertEquals(instant, (json \ "instant").as[Instant])
    assertEquals(offsetDateTime, (json \ "offsetDateTime").as[OffsetDateTime])
    assertEquals(localDate, (json \ "localDate").as[LocalDate])
    assertEquals(uuid, (json \ "uuid").as[UUID])
    assertEquals(JsString("2024-05-06"), Json.toJson(localDate))
    assertEquals(JsString(uuid.toString), Json.toJson(uuid))
  }

  @Test
  def composesFormatsAndWritesCustomJsonValues(): Unit = {
    val centsFormat: Format[BigDecimal] = Format[BigDecimal](
      Reads.of[Int].map(cents => BigDecimal(cents) / BigDecimal(100)),
      Writes[BigDecimal](amount => JsNumber((amount * BigDecimal(100)).toInt))
    )
    val labelWrites: Writes[String] = Writes[String](value => JsString(value.trim.toUpperCase))

    assertEquals(BigDecimal("12.34"), JsNumber(1234).as[BigDecimal](centsFormat))
    assertEquals(JsNumber(1234), Json.toJson(BigDecimal("12.34"))(centsFormat))
    assertEquals(JsString("PLAY JSON"), Json.toJson(" play json ")(labelWrites))

    val combined: JsObject = Json.obj("amount" -> Json.toJson(BigDecimal("12.34"))(centsFormat)) ++
      Json.obj("label" -> Json.toJson(" play json ")(labelWrites))
    assertEquals(BigDecimal("12.34"), (combined \ "amount").as[BigDecimal](centsFormat))
    assertEquals("PLAY JSON", (combined \ "label").as[String])
  }
}
