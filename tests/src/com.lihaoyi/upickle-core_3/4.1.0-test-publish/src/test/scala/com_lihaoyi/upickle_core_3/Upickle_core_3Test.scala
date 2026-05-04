/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_lihaoyi.upickle_core_3

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import upickle.core.Abort
import upickle.core.AbortException
import upickle.core.ArrVisitor
import upickle.core.BufferedValue
import upickle.core.ByteBuilder
import upickle.core.ByteOps
import upickle.core.CharBuilder
import upickle.core.CharOps
import upickle.core.LinkedHashMap as UpickleLinkedHashMap
import upickle.core.ObjVisitor
import upickle.core.ParseUtils
import upickle.core.RenderUtils
import upickle.core.StringVisitor
import upickle.core.TraceVisitor
import upickle.core.Visitor

import java.io.ByteArrayOutputStream
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters.*

class Upickle_core_3Test {
  @Test
  def bufferedValuesTransformNestedStructuresAndSortObjectKeys(): Unit = {
    val input: BufferedValue = BufferedValue.Obj(
      ArrayBuffer(
        BufferedValue.Str("z", 1) -> BufferedValue.Arr(
          ArrayBuffer(
            BufferedValue.True(2),
            BufferedValue.False(3),
            BufferedValue.Null(4),
            BufferedValue.Int32(7, 5),
            BufferedValue.Float64String("12.5", 6)
          ),
          2
        ),
        BufferedValue.Str("a", 7) -> BufferedValue.Str("text", 8)
      ),
      jsonableKeys = true,
      index = 0
    )

    val unsorted: String = BufferedValue.transform(input, RenderingVisitor)
    val sorted: String = BufferedValue.maybeSortKeysTransform(BufferedValue, input, true, RenderingVisitor)

    assertThat(unsorted).isEqualTo("{\"z\":[true,false,null,7,12.5],\"a\":\"text\"}")
    assertThat(sorted).isEqualTo("{\"a\":\"text\",\"z\":[true,false,null,7,12.5]}")
  }

  @Test
  def bufferedValueBuilderBuildsCompositeValuesThroughVisitorCallbacks(): Unit = {
    val objVisitor: ObjVisitor[BufferedValue, BufferedValue] = BufferedValue.Builder.visitObject(2, true, 10)

    objVisitor.visitKeyValue(BufferedValue.Builder.visitString("letters", 11))
    val arrVisitor: ArrVisitor[BufferedValue, BufferedValue] = BufferedValue.Builder.visitArray(2, 12)
    arrVisitor.visitValue(BufferedValue.Builder.visitChar('x', 13), 13)
    arrVisitor.visitValue(BufferedValue.Builder.visitBinary("yz".getBytes(StandardCharsets.UTF_8), 0, 2, 14), 14)
    objVisitor.visitValue(arrVisitor.visitEnd(15), 15)

    objVisitor.visitKeyValue(BufferedValue.Builder.visitString("ext", 16))
    objVisitor.visitValue(BufferedValue.Builder.visitExt(3.toByte, Array[Byte](9, 8, 7, 6), 1, 2, 17), 17)

    val built: BufferedValue = objVisitor.visitEnd(18)

    assertThat(BufferedValue.transform(built, RenderingVisitor))
      .isEqualTo("{\"letters\":[\"x\",binary(797a)],\"ext\":ext(3:0807)}")
  }

  @Test
  def bufferedValueScalarsPreserveIndexesAndPrimitivePayloads(): Unit = {
    val bytePayload: Array[Byte] = Array[Byte](10, 20, 30, 40)
    val values: Seq[BufferedValue] = Seq(
      BufferedValue.NumRaw(1.25d, 1),
      BufferedValue.Float32(2.5f, 2),
      BufferedValue.Int64(1234567890123L, 3),
      BufferedValue.UInt64(-1L, 4),
      BufferedValue.Num("-12.34e5", 3, 6, 5),
      BufferedValue.Binary(bytePayload, 1, 2, 6)
    )

    assertThat(values.map(_.index).asJava).containsExactly(1, 2, 3, 4, 5, 6)
    assertThat(values.map(value => BufferedValue.transform(value, RenderingVisitor)).asJava)
      .containsExactly("1.25", "2.5", "1234567890123", "18446744073709551615", "-12.34e5", "binary(141e)")
    assertThat(BufferedValue.ordinal(BufferedValue.Str("ordinal", 99))).isGreaterThanOrEqualTo(0)
    assertThat(BufferedValue.valueToSortKey(BufferedValue.Str("sort-key", 0))).isEqualTo("sort-key")
  }

  @Test
  def stringVisitorFormatsPrimitiveValuesAndRejectsUnsupportedBinary(): Unit = {
    val utf8: Array[Byte] = "héllo".getBytes(StandardCharsets.UTF_8)

    assertThat(StringVisitor.visitString("plain", 0)).isEqualTo("plain")
    assertThat(StringVisitor.visitChar('q', 1)).isEqualTo("q")
    assertThat(StringVisitor.visitInt32(42, 2)).isEqualTo("42")
    assertThat(StringVisitor.visitInt64(1234567890123L, 3)).isEqualTo("1234567890123")
    assertThat(StringVisitor.visitFloat32(12.5f, 4)).isEqualTo("12.5")
    assertThat(StringVisitor.visitFloat64(3.0d, 5)).isEqualTo("3")
    assertThat(StringVisitor.visitTrue(6)).isEqualTo("true")
    assertThat(StringVisitor.visitFalse(7)).isEqualTo("false")

    assertThatThrownBy(() => StringVisitor.visitBinary(utf8, 0, utf8.length, 8))
      .isInstanceOf(classOf[upickle.core.Abort])
  }

  @Test
  def buildersGrowAppendFlushAndResetContents(): Unit = {
    val bytes: ByteBuilder = new ByteBuilder(2)
    bytes.append('H'.toByte)
    bytes.append('i'.toByte)
    bytes.appendAll(" there".getBytes(StandardCharsets.UTF_8), 6)
    assertThat(bytes.getLength).isEqualTo(8)
    assertThat(bytes.makeString()).isEqualTo("Hi there")

    val byteOutput: ByteArrayOutputStream = new ByteArrayOutputStream()
    bytes.writeOutToIfLongerThan(byteOutput, 3)
    assertThat(byteOutput.toString(StandardCharsets.UTF_8)).isEqualTo("Hi there")
    assertThat(bytes.getLength).isEqualTo(0)

    bytes.appendAll("reset".getBytes(StandardCharsets.UTF_8), 5)
    bytes.reset()
    assertThat(bytes.getLength).isZero()

    val chars: CharBuilder = new CharBuilder(1)
    chars.append('O')
    chars.append('K')
    chars.appendAll("!?!".toCharArray, 1, 2)
    assertThat(chars.makeString()).isEqualTo("OK?!")

    val writer: StringWriter = new StringWriter()
    chars.writeOutToIfLongerThan(writer, 2)
    assertThat(writer.toString).isEqualTo("OK?!")
    assertThat(chars.getLength).isEqualTo(0)
  }

  @Test
  def parseAndRenderUtilitiesHandleUtf8NumbersAndEscaping(): Unit = {
    val text: String = "héllo-世界"
    val bytes: Array[Byte] = text.getBytes(StandardCharsets.UTF_8)
    val encodedBytes: String = ParseUtils.bytesToString(bytes)

    assertThat(ParseUtils.stringToBytes(encodedBytes)).isEqualTo(bytes)
    assertThat(ParseUtils.parseLong("prefix-123456789suffix", 6, 16)).isEqualTo(-123456789L)
    assertThat(ParseUtils.parseIntegralNum("12.345e6", 2, 6, 0)).isEqualTo(12345000L)
    assertThat(RenderUtils.intStringSize(0)).isEqualTo(1)
    assertThat(RenderUtils.intStringSize(1000)).isEqualTo(4)
    assertThat(RenderUtils.longStringSize(Long.MinValue)).isEqualTo(20)
    assertThat(RenderUtils.toHex(15)).isEqualTo('f')

    val charEscaped: CharBuilder = RenderUtils.escapeChar(
      new CharBuilder(4),
      new CharBuilder(4),
      "quote:\" slash:/ backslash:\\ newline:\n tab:\t control:\u0001",
      true,
      false
    )
    assertThat(charEscaped.makeString()).contains("quote:\\\"")
    assertThat(charEscaped.makeString()).contains("slash:/")
    assertThat(charEscaped.makeString()).contains("backslash:\\\\")
    assertThat(charEscaped.makeString()).contains("newline:\\n")
    assertThat(charEscaped.makeString()).contains("tab:\\t")
    assertThat(charEscaped.makeString()).contains("control:\\u0001")

    val byteEscaped: ByteBuilder = new ByteBuilder(4)
    RenderUtils.escapeByte(new CharBuilder(4), byteEscaped, "line\n", false, false)
    assertThat(byteEscaped.makeString()).isEqualTo("line\\n")
  }

  @Test
  def byteAndCharOpsExposeUnsignedComparisonsAndSlices(): Unit = {
    val bytes: Array[Byte] = "abcdef".getBytes(StandardCharsets.UTF_8)
    val chars: Array[Char] = "uvwxyz".toCharArray

    assertThat(ByteOps.toInt(65.toByte)).isEqualTo(65)
    assertThat(ByteOps.toUnsignedInt((-1).toByte)).isEqualTo(255)
    assertThat(ByteOps.lessThan('a'.toByte, 'b')).isTrue()
    assertThat(ByteOps.within('a', 'c'.toByte, 'z')).isTrue()
    assertThat(ByteOps.newString(bytes, 1, 3)).isEqualTo("bcd")

    assertThat(CharOps.toInt('A')).isEqualTo(65)
    assertThat(CharOps.toUnsignedInt('λ')).isEqualTo('λ'.toInt)
    assertThat(CharOps.lessThan('a', 'b')).isTrue()
    assertThat(CharOps.within('a', 'm', 'z')).isTrue()
    assertThat(CharOps.newString(chars, 2, 3)).isEqualTo("wxy")
  }

  @Test
  def linkedHashMapPreservesInsertionOrderAcrossMutations(): Unit = {
    val map: UpickleLinkedHashMap[String, Int] = UpickleLinkedHashMap()
    map.update("first", 1)
    map.update("second", 2)
    map.update("third", 3)

    map.update("second", 20)

    assertThat(map.keysIterator.toSeq.asJava).containsExactly("first", "second", "third")
    assertThat(map("second")).isEqualTo(20)

    val removed: Option[Int] = map.remove("first")
    map.update("fourth", 4)

    assertThat(removed).isEqualTo(Some(1))
    assertThat(map.iterator.map { case (key, value) => s"$key=$value" }.toSeq.asJava)
      .containsExactly("second=20", "third=3", "fourth=4")
  }

  @Test
  def traceVisitorReportsNestedPathForVisitorFailures(): Unit = {
    val input: BufferedValue = BufferedValue.Obj(
      ArrayBuffer(
        BufferedValue.Str("items", 1) -> BufferedValue.Arr(
          ArrayBuffer(
            BufferedValue.Str("ok", 2),
            BufferedValue.Int32(99, 3)
          ),
          4
        )
      ),
      jsonableKeys = true,
      index = 0
    )

    val thrown: TraceVisitor.TraceException = assertThrows(
      classOf[TraceVisitor.TraceException],
      () => TraceVisitor.withTrace[Unit, Unit](true, RejectingIntVisitor) { (visitor: Visitor[Unit, Unit]) =>
        BufferedValue.transform(input, visitor)
      }
    )

    assertThat(thrown.jsonPath).isEqualTo("$['items'][1]")
    assertThat(thrown.getMessage).isEqualTo("$['items'][1]")
    assertThat(thrown.getCause).isInstanceOf(classOf[AbortException])
  }

  private object RejectingIntVisitor extends Visitor[Unit, Unit] {
    override def visitArray(length: Int, index: Int): ArrVisitor[Unit, Unit] = new ArrVisitor[Unit, Unit] {
      override def subVisitor: Visitor[?, ?] = RejectingIntVisitor

      override def visitValue(value: Unit, index: Int): Unit = ()

      override def visitEnd(index: Int): Unit = ()
    }

    override def visitObject(length: Int, jsonableKeys: Boolean, index: Int): ObjVisitor[Unit, Unit] = new ObjVisitor[Unit, Unit] {
      override def visitKey(index: Int): Visitor[?, ?] = StringVisitor

      override def visitKeyValue(value: Any): Unit = ()

      override def subVisitor: Visitor[?, ?] = RejectingIntVisitor

      override def visitValue(value: Unit, index: Int): Unit = ()

      override def visitEnd(index: Int): Unit = ()
    }

    override def visitNull(index: Int): Unit = ()

    override def visitFalse(index: Int): Unit = ()

    override def visitTrue(index: Int): Unit = ()

    override def visitFloat64StringParts(value: CharSequence, decIndex: Int, expIndex: Int, index: Int): Unit = ()

    override def visitFloat64(value: Double, index: Int): Unit = ()

    override def visitFloat32(value: Float, index: Int): Unit = ()

    override def visitInt32(value: Int, index: Int): Unit = throw Abort("integers are rejected")

    override def visitInt64(value: Long, index: Int): Unit = ()

    override def visitUInt64(value: Long, index: Int): Unit = ()

    override def visitFloat64String(value: String, index: Int): Unit = ()

    override def visitString(value: CharSequence, index: Int): Unit = ()

    override def visitChar(value: Char, index: Int): Unit = ()

    override def visitBinary(bytes: Array[Byte], offset: Int, len: Int, index: Int): Unit = ()

    override def visitExt(tag: Byte, bytes: Array[Byte], offset: Int, len: Int, index: Int): Unit = ()
  }

  private object RenderingVisitor extends Visitor[String, String] {
    override def visitArray(length: Int, index: Int): ArrVisitor[String, String] = new ArrVisitor[String, String] {
      private val values: ArrayBuffer[String] = ArrayBuffer.empty[String]

      override def subVisitor: Visitor[?, ?] = RenderingVisitor

      override def visitValue(value: String, index: Int): Unit = values += value

      override def visitEnd(index: Int): String = values.mkString("[", ",", "]")
    }

    override def visitObject(length: Int, jsonableKeys: Boolean, index: Int): ObjVisitor[String, String] = new ObjVisitor[String, String] {
      private val entries: ArrayBuffer[(String, String)] = ArrayBuffer.empty[(String, String)]
      private var currentKey: String = ""

      override def visitKey(index: Int): Visitor[?, ?] = RenderingVisitor

      override def visitKeyValue(value: Any): Unit = currentKey = value.asInstanceOf[String]

      override def subVisitor: Visitor[?, ?] = RenderingVisitor

      override def visitValue(value: String, index: Int): Unit = entries += (currentKey -> value)

      override def visitEnd(index: Int): String = entries.map { case (key, value) => s"$key:$value" }.mkString("{", ",", "}")
    }

    override def visitNull(index: Int): String = "null"

    override def visitFalse(index: Int): String = "false"

    override def visitTrue(index: Int): String = "true"

    override def visitFloat64StringParts(value: CharSequence, decIndex: Int, expIndex: Int, index: Int): String = value.toString

    override def visitFloat64(value: Double, index: Int): String = value.toString

    override def visitFloat32(value: Float, index: Int): String = value.toString

    override def visitInt32(value: Int, index: Int): String = value.toString

    override def visitInt64(value: Long, index: Int): String = value.toString

    override def visitUInt64(value: Long, index: Int): String = java.lang.Long.toUnsignedString(value)

    override def visitFloat64String(value: String, index: Int): String = value

    override def visitString(value: CharSequence, index: Int): String = quote(value.toString)

    override def visitChar(value: Char, index: Int): String = quote(value.toString)

    override def visitBinary(bytes: Array[Byte], offset: Int, len: Int, index: Int): String =
      s"binary(${hex(bytes, offset, len)})"

    override def visitExt(tag: Byte, bytes: Array[Byte], offset: Int, len: Int, index: Int): String =
      s"ext($tag:${hex(bytes, offset, len)})"

    private def quote(value: String): String = {
      val escaped: String = value.flatMap {
        case '\\' => "\\\\"
        case '\"' => "\\\""
        case '\n' => "\\n"
        case '\r' => "\\r"
        case '\t' => "\\t"
        case char => char.toString
      }
      s"\"$escaped\""
    }

    private def hex(bytes: Array[Byte], offset: Int, len: Int): String =
      bytes.slice(offset, offset + len).map(byte => f"${byte & 0xff}%02x").mkString
  }
}
