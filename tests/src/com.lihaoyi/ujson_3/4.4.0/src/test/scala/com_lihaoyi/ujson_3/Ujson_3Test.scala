/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_lihaoyi.ujson_3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringWriter
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class Ujson_3Test {
  private val utf8 = StandardCharsets.UTF_8

  @Test
  def parsesJsonFromAllSupportedReadableSources(): Unit = {
    val json = """{"name":"ujson","numbers":[1,2.5,-3],"enabled":true,"none":null}"""
    val expected = ujson.read(ujson.Readable.fromString(json))

    val tempFile = Files.createTempFile("ujson-readable", ".json")
    Files.writeString(tempFile, json, utf8)

    val values = Seq(
      ujson.read(ujson.Readable.fromCharSequence(new java.lang.StringBuilder(json))),
      ujson.read(ujson.Readable.fromByteArray(json.getBytes(utf8))),
      ujson.read(ujson.Readable.fromByteBuffer(ByteBuffer.wrap(json.getBytes(utf8)))),
      ujson.read(ujson.Readable.fromPath(tempFile)),
      ujson.read(ujson.Readable.fromFile(tempFile.toFile))
    )

    values.foreach { value =>
      assertEquals(expected, value)
      assertEquals("ujson", value(strSelector("name")).str)
      assertEquals(List(1.0, 2.5, -3.0), value(strSelector("numbers")).arr.map(_.num).toList)
      assertTrue(value(strSelector("enabled")).bool)
      assertTrue(value(strSelector("none")).isNull)
    }
  }

  @Test
  def parsesUtf8InputStreamsAndTransformsThroughAstVisitor(): Unit = {
    val json = """{"message":"Grüße ☃","nested":{"count":3},"items":[false,true]}"""
    val inputStream = new ByteArrayInputStream(json.getBytes(utf8))
    val parsedFromStream: ujson.Value = ujson.InputStreamParser.transform(inputStream, ujson.Value)
    val transformedFromReadable: ujson.Value = ujson.transform(ujson.Readable.fromString(json), ujson.Value)

    assertEquals(transformedFromReadable, parsedFromStream)
    assertEquals("Grüße ☃", parsedFromStream(strSelector("message")).str)
    assertEquals(3.0, parsedFromStream(strSelector("nested"))(strSelector("count")).num, 0.0)
    assertFalse(parsedFromStream(strSelector("items"))(intSelector(0)).bool)
    assertTrue(parsedFromStream(strSelector("items"))(intSelector(1)).bool)
  }

  @Test
  def rendersValuesToStringsBytesWritersAndOutputStreams(): Unit = {
    val value = ujson.Obj.from(Seq[(String, ujson.Value)](
      "name" -> ujson.Str("ujson"),
      "enabled" -> ujson.True,
      "numbers" -> ujson.Arr(Seq[ujson.Value](ujson.Num(1), ujson.Num(2.5))*),
      "empty" -> ujson.Null
    ))

    val compact = ujson.write(value)
    val rendered = ujson.read(ujson.Readable.fromString(compact))
    assertEquals("ujson", rendered(strSelector("name")).str)
    assertTrue(rendered(strSelector("enabled")).bool)
    assertEquals(List(1.0, 2.5), rendered(strSelector("numbers")).arr.map(_.num).toList)
    assertTrue(rendered(strSelector("empty")).isNull)
    assertFalse(compact.contains("\n"))

    val byteArrayResult = new String(ujson.writeToByteArray(value), utf8)
    assertRenderedSampleObject(byteArrayResult)
    assertFalse(byteArrayResult.contains("\n"))

    val writer = new StringWriter()
    ujson.writeTo(value, writer, -1, false, true)
    assertRenderedSampleObject(writer.toString)
    assertFalse(writer.toString.contains("\n"))

    val outputStream = new ByteArrayOutputStream()
    ujson.writeToOutputStream(value, outputStream, -1, false, true)
    val outputStreamResult = outputStream.toString(utf8)
    assertRenderedSampleObject(outputStreamResult)
    assertFalse(outputStreamResult.contains("\n"))

    val pretty = ujson.write(value, 2, false)
    assertTrue(pretty.contains(System.lineSeparator()) || pretty.contains("\n"))
    assertRenderedSampleObject(pretty)
  }

  @Test
  def reformatsJsonWithoutChangingItsData(): Unit = {
    val raw = """{"z":[3,2,1],"text":"snowman ☃","obj":{"b":false,"a":true}}"""
    val compact = ujson.reformat(ujson.Readable.fromString(raw), -1, false, false)
    val pretty = ujson.reformat(ujson.Readable.fromString(raw), 2, false, false)

    Seq(compact, pretty).foreach { reformatted =>
      val value = ujson.read(ujson.Readable.fromString(reformatted))
      assertEquals(List(3.0, 2.0, 1.0), value(strSelector("z")).arr.map(_.num).toList)
      assertEquals("snowman ☃", value(strSelector("text")).str)
      assertFalse(value(strSelector("obj"))(strSelector("b")).bool)
      assertTrue(value(strSelector("obj"))(strSelector("a")).bool)
    }
    assertFalse(compact.contains("\n"))
    assertTrue(pretty.contains("\n"))

    val writer = new StringWriter()
    ujson.reformatTo(ujson.Readable.fromString(raw), writer, -1, false, false)
    assertReformattedContent(writer.toString)
    assertFalse(writer.toString.contains("\n"))

    val outputStream = new ByteArrayOutputStream()
    ujson.reformatToOutputStream(ujson.Readable.fromString(raw), outputStream, -1, false, true)
    val outputStreamResult = outputStream.toString(utf8)
    assertReformattedContent(outputStreamResult)
    assertFalse(outputStreamResult.contains("\n"))

    val byteArrayResult = new String(ujson.reformatToByteArray(ujson.Readable.fromString(raw), -1, false, false), utf8)
    assertReformattedContent(byteArrayResult)
    assertFalse(byteArrayResult.contains("\n"))
  }

  @Test
  def preservesObjectFieldInsertionOrderWhenParsedBuiltAndRendered(): Unit = {
    val parsed = ujson.read(ujson.Readable.fromString("""{"first":1,"second":2,"third":3}"""))
    assertEquals(List("first", "second", "third"), parsed.obj.keys.toList)
    assertFieldOrder(ujson.write(parsed), Seq("first", "second", "third"))

    val built = ujson.Obj.from(Seq[(String, ujson.Value)](
      "zeta" -> ujson.Num(26),
      "alpha" -> ujson.Num(1),
      "middle" -> ujson.Str("kept")
    ))

    assertEquals(List("zeta", "alpha", "middle"), built.obj.keys.toList)
    assertFieldOrder(ujson.write(built), Seq("zeta", "alpha", "middle"))
  }

  @Test
  def sortsObjectFieldsRecursivelyWhenRequested(): Unit = {
    val value = ujson.Obj.from(Seq[(String, ujson.Value)](
      "z" -> ujson.Num(1),
      "a" -> ujson.Obj.from(Seq[(String, ujson.Value)](
        "delta" -> ujson.Bool(false),
        "beta" -> ujson.Bool(true)
      )),
      "m" -> ujson.Arr(Seq[ujson.Value](
        ujson.Obj.from(Seq[(String, ujson.Value)](
          "two" -> ujson.Str("second"),
          "one" -> ujson.Str("first")
        ))
      )*)
    ))

    val unsorted = ujson.write(value, -1, false, false)
    assertFieldOrder(unsorted, Seq("z", "a", "m"))
    assertFieldOrder(unsorted, Seq("delta", "beta"))
    assertFieldOrder(unsorted, Seq("two", "one"))

    val sorted = ujson.write(value, -1, false, true)
    assertFieldOrder(sorted, Seq("a", "m", "z"))
    assertFieldOrder(sorted, Seq("beta", "delta"))
    assertFieldOrder(sorted, Seq("one", "two"))
    assertSortedObjectContent(sorted)

    val reformatted = ujson.reformat(ujson.Readable.fromString(unsorted), -1, false, true)
    assertFieldOrder(reformatted, Seq("a", "m", "z"))
    assertFieldOrder(reformatted, Seq("beta", "delta"))
    assertFieldOrder(reformatted, Seq("one", "two"))
    assertSortedObjectContent(reformatted)
  }

  @Test
  def supportsValueAccessorsSelectorsMutationAndDeepCopy(): Unit = {
    val original = ujson.read(
      """{"cart":{"items":[{"sku":"a","price":2.5},{"sku":"b","price":3.5}],"discount":0},"paid":false}"""
    )
    val copy = ujson.copy(original)

    assertNotSame(original, copy)
    copy(strSelector("cart"))(strSelector("items"))(intSelector(1)).update(strSelector("price"), ujson.Num(4.75))
    copy(strSelector("cart")).update(strSelector("discount"), (oldValue: ujson.Value) => ujson.Num(oldValue.num + 1.25))
    copy.update(strSelector("paid"), ujson.True)

    assertEquals(3.5, original(strSelector("cart"))(strSelector("items"))(intSelector(1))(strSelector("price")).num, 0.0)
    assertEquals(4.75, copy(strSelector("cart"))(strSelector("items"))(intSelector(1))(strSelector("price")).num, 0.0)
    assertEquals(1.25, copy(strSelector("cart"))(strSelector("discount")).num, 0.0)
    assertTrue(copy(strSelector("paid")).bool)

    val copiedItems = copy(strSelector("cart"))(strSelector("items")).arr
    assertEquals(2, copiedItems.size)
    assertEquals("a", copiedItems(0)(strSelector("sku")).str)
  }

  @Test
  def exposesTypedValuesAndOptionalAccessors(): Unit = {
    val stringValue = ujson.Str("text")
    val numberValue = ujson.Num(42.25)
    val trueValue = ujson.Bool(true)
    val falseValue = ujson.Bool(false)
    val arrayValue = ujson.Arr(Seq[ujson.Value](stringValue, numberValue, falseValue)*)
    val objectValue = ujson.Obj.from(Seq[(String, ujson.Value)]("array" -> arrayValue, "null" -> ujson.Null))

    assertEquals("text", stringValue.value)
    assertEquals("text", stringValue.str)
    assertTrue(stringValue.strOpt.contains("text"))
    assertTrue(stringValue.numOpt.isEmpty)

    assertEquals(42.25, numberValue.value, 0.0)
    assertEquals(42.25, numberValue.num, 0.0)
    assertTrue(numberValue.numOpt.isDefined)
    assertTrue(numberValue.strOpt.isEmpty)

    assertTrue(trueValue.value)
    assertFalse(falseValue.value)
    assertTrue(trueValue.boolOpt.isDefined)
    assertTrue(ujson.Null.isNull)

    assertEquals(3, arrayValue.arr.size)
    assertTrue(arrayValue.arrOpt.isDefined)
    assertTrue(arrayValue.objOpt.isEmpty)
    assertEquals(2, objectValue.obj.size)
    assertTrue(objectValue.objOpt.isDefined)

    val invalidData = assertThrows(
      classOf[ujson.Value.InvalidData],
      () => {
        stringValue.num
        ()
      }
    )
    assertEquals(stringValue, invalidData.data)
    assertTrue(invalidData.msg.nonEmpty)
  }

  @Test
  def escapesUnicodeWhenRequestedAndPreservesItOtherwise(): Unit = {
    val value = ujson.Str("snowman ☃, greek λ, newline\n")

    val escaped = ujson.write(value, -1, true)
    val unescaped = ujson.write(value, -1, false)

    assertTrue(escaped.contains("\\u2603"))
    assertTrue(escaped.toLowerCase.contains("\\u03bb"))
    assertTrue(escaped.contains("\\n"))
    assertTrue(unescaped.contains("☃"))
    assertTrue(unescaped.contains("λ"))
    assertEquals(value, ujson.read(ujson.Readable.fromString(escaped)))
    assertEquals(value, ujson.read(ujson.Readable.fromString(unescaped)))
  }

  @Test
  def validatesJsonAndReportsParsingFailures(): Unit = {
    ujson.validate(ujson.Readable.fromString("""{"valid":[1,true,null]}"""))

    val parseException = assertThrows(
      classOf[ujson.ParseException],
      () => {
        ujson.read(ujson.Readable.fromString("""{"invalid":]"""))
        ()
      }
    )
    assertTrue(parseException.clue.nonEmpty)
    assertTrue(parseException.index >= 0)
    assertEquals(parseException, parseException.copy(parseException.clue, parseException.index))

    val incompleteException = assertThrows(
      classOf[ujson.IncompleteParseException],
      () => {
        ujson.read(ujson.Readable.fromString("""{"unfinished":"value"""))
        ()
      }
    )
    assertTrue(incompleteException.msg.nonEmpty)
    assertEquals(incompleteException, incompleteException.copy(incompleteException.msg))
  }

  private def assertRenderedSampleObject(json: String): Unit = {
    val value = ujson.read(ujson.Readable.fromString(json))
    assertEquals("ujson", value(strSelector("name")).str)
    assertTrue(value(strSelector("enabled")).bool)
    assertEquals(List(1.0, 2.5), value(strSelector("numbers")).arr.map(_.num).toList)
    assertTrue(value(strSelector("empty")).isNull)
  }

  private def assertReformattedContent(json: String): Unit = {
    val value = ujson.read(ujson.Readable.fromString(json))
    assertEquals(List(3.0, 2.0, 1.0), value(strSelector("z")).arr.map(_.num).toList)
    assertEquals("snowman ☃", value(strSelector("text")).str)
    assertFalse(value(strSelector("obj"))(strSelector("b")).bool)
    assertTrue(value(strSelector("obj"))(strSelector("a")).bool)
  }

  private def assertSortedObjectContent(json: String): Unit = {
    val value = ujson.read(ujson.Readable.fromString(json))
    assertEquals(1.0, value(strSelector("z")).num, 0.0)
    assertTrue(value(strSelector("a"))(strSelector("beta")).bool)
    assertFalse(value(strSelector("a"))(strSelector("delta")).bool)
    assertEquals("first", value(strSelector("m"))(intSelector(0))(strSelector("one")).str)
    assertEquals("second", value(strSelector("m"))(intSelector(0))(strSelector("two")).str)
  }

  private def assertFieldOrder(json: String, fields: Seq[String]): Unit = {
    val positions = fields.map { field => json.indexOf(s"\"$field\"") }

    positions.foreach(position => assertTrue(position >= 0))
    assertEquals(positions.sorted, positions)
  }

  private def strSelector(key: String): ujson.Value.Selector = ujson.Value.Selector.StringSelector(key)

  private def intSelector(index: Int): ujson.Value.Selector = ujson.Value.Selector.IntSelector(index)
}
