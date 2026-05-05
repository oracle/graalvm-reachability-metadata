/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_lihaoyi.upickle_3

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import upickle.default.*
import upickle.implicits.key

import java.io.ByteArrayOutputStream
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.util.Locale

final case class Address(street: String, zipCode: Int) derives ReadWriter

final case class Customer(
    @key("customer_name") name: String,
    age: Int,
    address: Address,
    tags: Vector[String],
    scores: Map[String, Double],
    nickname: Option[String]
) derives ReadWriter

sealed trait InventoryEvent derives ReadWriter

object InventoryEvent {
  final case class Added(sku: String, quantity: Int, customer: Customer) extends InventoryEvent derives ReadWriter
  final case class Removed(sku: String, reason: Option[String]) extends InventoryEvent derives ReadWriter
  final case class Recounted(sku: String, previous: Int, current: Int) extends InventoryEvent derives ReadWriter
}

final case class Email(value: String)

object Email {
  given ReadWriter[Email] = readwriter[String].bimap[Email](
    email => email.value,
    value => Email(value.trim.toLowerCase(Locale.ROOT))
  )
}

final case class ProductCode(prefix: String, number: Int)

object ProductCode {
  given ReadWriter[ProductCode] = stringKeyRW(readwriter[String].bimap[ProductCode](
    code => s"${code.prefix}-${code.number}",
    value => {
      val parts: Array[String] = value.split("-", 2)
      ProductCode(parts(0), parts(1).toInt)
    }
  ))
}

final case class DefaultsEnabled(name: String, active: Boolean = true, aliases: List[String] = Nil) derives ReadWriter

class Upickle_3Test {
  @Test
  def readsAndWritesNestedCaseClassesWithCollectionsOptionsAndRenamedFields(): Unit = {
    val customer: Customer = Customer(
      name = "Ada Lovelace",
      age = 36,
      address = Address("St James's Square", 101),
      tags = Vector("founder", "math", "unicode-☃"),
      scores = Map("analysis" -> 99.5, "poetry" -> 88.25),
      nickname = Some("enchantress")
    )

    val jsonText: String = write(customer, 2, false, true)
    val jsonValue: ujson.Value = ujson.read(jsonText)

    assertEquals("Ada Lovelace", jsonValue(objKey("customer_name")).str)
    assertFalse(jsonValue.obj.contains("name"))
    assertEquals(36.0, jsonValue(objKey("age")).num, 0.0)
    assertEquals("St James's Square", jsonValue(objKey("address"))(objKey("street")).str)
    assertEquals(101.0, jsonValue(objKey("address"))(objKey("zipCode")).num, 0.0)
    assertEquals(List("founder", "math", "unicode-☃"), jsonValue(objKey("tags")).arr.map(_.str).toList)
    assertEquals("enchantress", jsonValue(objKey("nickname")).str)

    val parsed: Customer = read[Customer](jsonText)
    assertEquals(customer, parsed)

    val withoutNickname: Customer = customer.copy(nickname = None)
    val withoutNicknameJson: ujson.Value = writeJs(withoutNickname)
    assertTrue(withoutNicknameJson(objKey("nickname")).isNull)
    assertEquals(withoutNickname, read[Customer](withoutNicknameJson))
  }

  @Test
  def roundTripsSealedTraitHierarchiesThroughJsonAndMessagePack(): Unit = {
    val customer: Customer = Customer(
      "Grace Hopper",
      85,
      Address("Arlington", 42),
      Vector("compiler", "navy"),
      Map("debugging" -> 100.0),
      None
    )
    val events: List[InventoryEvent] = List(
      InventoryEvent.Added("BOOK-1", 4, customer),
      InventoryEvent.Removed("BOOK-2", Some("damaged")),
      InventoryEvent.Recounted("BOOK-3", previous = 7, current = 9)
    )

    val jsonText: String = write(events)
    assertEquals(events, read[List[InventoryEvent]](jsonText))

    val jsonAst: ujson.Value = writeJs(events)
    assertTrue(jsonAst.arr.nonEmpty)
    assertEquals(events, read[List[InventoryEvent]](jsonAst))

    val binary: Array[Byte] = writeBinary(events)
    assertTrue(binary.length > 0)
    assertEquals(events, readBinary[List[InventoryEvent]](upack.Readable.fromByteArray(binary)))
  }

  @Test
  def supportsPrimitiveCollectionsTuplesAndMaps(): Unit = {
    val payload: Map[String, List[Option[Int]]] = Map(
      "present" -> List(Some(1), Some(2), Some(3)),
      "mixed" -> List(Some(4), None, Some(6)),
      "empty" -> Nil
    )

    val jsonText: String = write(payload, -1, false, true)
    assertEquals(payload, read[Map[String, List[Option[Int]]]](jsonText))

    val tuple: (String, Int, Boolean, Option[Double]) = ("tuple", 7, true, Some(2.5d))
    assertEquals(tuple, read[(String, Int, Boolean, Option[Double])](write(tuple)))

    val numericKeyedMap: Map[Int, String] = Map(1 -> "one", 2 -> "two", 10 -> "ten")
    val numericKeyedJson: ujson.Value = writeJs(numericKeyedMap)
    assertEquals("one", numericKeyedJson(objKey("1")).str)
    assertEquals(numericKeyedMap, read[Map[Int, String]](numericKeyedJson))

    val tupleValues: List[(String, Int, Boolean)] = List(("one", 1, true), ("two", 2, false))
    assertEquals(tupleValues, read[List[(String, Int, Boolean)]](write(tupleValues)))
  }

  @Test
  def usesCustomReadWritersForValueClassesAndAppliesReadMapping(): Unit = {
    val emails: List[Email] = List(Email("Help@Example.COM"), Email("Admin@Example.COM"))

    val jsonText: String = write(emails)
    val jsonValue: ujson.Value = ujson.read(jsonText)
    assertEquals(List("Help@Example.COM", "Admin@Example.COM"), jsonValue.arr.map(_.str).toList)

    val parsed: List[Email] = read[List[Email]]("""[" HELP@EXAMPLE.COM ", " ADMIN@EXAMPLE.COM "]""")
    assertEquals(List(Email("help@example.com"), Email("admin@example.com")), parsed)
  }

  @Test
  def supportsCustomTypesAsJsonObjectKeysWithStringKeyReadWriter(): Unit = {
    val stockByProduct: Map[ProductCode, Int] = Map(
      ProductCode("BOOK", 1) -> 12,
      ProductCode("PEN", 9) -> 5
    )

    val jsonValue: ujson.Value = writeJs(stockByProduct)
    assertEquals(12.0, jsonValue(objKey("BOOK-1")).num, 0.0)
    assertEquals(5.0, jsonValue(objKey("PEN-9")).num, 0.0)
    assertEquals(stockByProduct, read[Map[ProductCode, Int]](jsonValue))

    val jsonText: String = write(stockByProduct, -1, false, true)
    assertTrue(jsonText.startsWith("{"))
    assertFalse(jsonText.startsWith("["))
    assertEquals(stockByProduct, read[Map[ProductCode, Int]](jsonText))
  }

  @Test
  def readsDefaultsAndReportsInvalidInputErrors(): Unit = {
    val defaults: DefaultsEnabled = read[DefaultsEnabled]("""{"name":"defaults"}""")
    assertEquals(DefaultsEnabled("defaults"), defaults)

    val explicit: DefaultsEnabled = DefaultsEnabled("custom", active = false, aliases = List("x", "y"))
    assertEquals(explicit, read[DefaultsEnabled](write(explicit)))

    assertThrows(
      classOf[upickle.core.TraceVisitor.TraceException],
      () => {
        read[Customer]("""{"customer_name":"missing-required-fields"}""")
        ()
      }
    )

    assertThrows(
      classOf[upickle.core.TraceVisitor.TraceException],
      () => {
        read[List[Int]]("""{"not":"a-list"}""")
        ()
      }
    )
  }

  @Test
  def transformsBetweenCompatibleReadersAndWriters(): Unit = {
    val customer: Customer = Customer(
      "Dorothy Vaughan",
      98,
      Address("Hampton", 1),
      Vector("manager", "fortran"),
      Map("leadership" -> 100.0),
      Some("human computer")
    )

    val jsonValue: ujson.Value = transform(customer).to[ujson.Value]
    assertEquals("Dorothy Vaughan", jsonValue(objKey("customer_name")).str)
    assertEquals(customer, transform(jsonValue).to[Customer])
  }

  @Test
  def writesToTextAndByteTargetsWithConsistentJsonContent(): Unit = {
    val customer: Customer = Customer(
      "Katherine Johnson",
      101,
      Address("White Sulphur Springs", 314),
      Vector("space", "math"),
      Map("orbital" -> 100.0, "navigation" -> 99.0),
      Some("computer")
    )

    val stringWriter: StringWriter = new StringWriter()
    writeTo(customer, stringWriter, 2, false, true)
    val fromWriter: String = stringWriter.toString
    assertTrue(fromWriter.contains("\n"))
    assertEquals(customer, read[Customer](fromWriter))

    val jsonBytes: Array[Byte] = writeToByteArray(customer, -1, false, true)
    val fromBytes: String = new String(jsonBytes, StandardCharsets.UTF_8)
    assertFalse(fromBytes.contains("\n"))
    assertEquals(customer, read[Customer](ujson.Readable.fromByteArray(jsonBytes)))

    val outputStream: ByteArrayOutputStream = new ByteArrayOutputStream()
    writeToOutputStream(customer, outputStream, -1, false, true)
    assertArrayEquals(jsonBytes, outputStream.toByteArray)
  }

  @Test
  def escapesUnicodeAndSortsKeysWhenRequested(): Unit = {
    val customer: Customer = Customer(
      "Zoë ☃",
      28,
      Address("München", 80331),
      Vector("λ", "雪"),
      Map("zeta" -> 1.0, "alpha" -> 2.0),
      None
    )

    val unescaped: String = write(customer, -1, false, false)
    val escapedSorted: String = write(customer, -1, true, true)

    assertTrue(unescaped.contains("Zoë ☃"))
    assertTrue(unescaped.contains("München"))
    val lowerCaseEscapedSorted: String = escapedSorted.toLowerCase(Locale.ROOT)
    assertTrue(lowerCaseEscapedSorted.contains("zo\\u00eb \\u2603"))
    assertTrue(lowerCaseEscapedSorted.contains("m\\u00fcnchen"))
    assertNotEquals(unescaped, escapedSorted)
    assertEquals(customer, read[Customer](escapedSorted))

    assertFieldOrder(escapedSorted, Seq("address", "age", "customer_name", "nickname", "scores", "tags"))
    assertFieldOrder(escapedSorted, Seq("alpha", "zeta"))
  }

  private def objKey(value: String): ujson.Value.Selector = ujson.Value.Selector.StringSelector(value)

  private def assertFieldOrder(jsonText: String, fields: Seq[String]): Unit = {
    val positions: Seq[Int] = fields.map(field => jsonText.indexOf(s"\"$field\""))
    assertTrue(positions.forall(_ >= 0), s"Expected all fields $fields to appear in $jsonText")
    assertEquals(positions.sorted, positions, s"Expected fields $fields to appear in order in $jsonText")
  }
}
