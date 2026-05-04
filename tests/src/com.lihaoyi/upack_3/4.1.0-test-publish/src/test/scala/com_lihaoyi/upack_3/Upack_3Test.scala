/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_lihaoyi.upack_3

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import upack.*

class Upack_3Test {
  @Test
  def writesAndReadsScalarMessagePackValues(): Unit = {
    assertBytes(upack.write(Null), 0xc0)
    assertBytes(upack.write(True), 0xc3)
    assertBytes(upack.write(False), 0xc2)
    assertBytes(upack.write(Bool(true)), 0xc3)
    assertBytes(upack.write(Bool(false)), 0xc2)

    assertEquals(Null, readBytes(Array(0xc0.toByte)))
    assertTrue(readBytes(Array(0xc3.toByte)).bool)
    assertFalse(readBytes(Array(0xc2.toByte)).bool)
    assertEquals(127, readBytes(upack.write(Int32(127))).int32)
    assertEquals(-1, readBytes(upack.write(Int32(-1))).int32)
    assertEquals(Long.MaxValue, readBytes(upack.write(Int64(Long.MaxValue))).int64)
    assertEquals(-1L, readBytes(upack.write(UInt64(-1L))).int64)
    assertEquals(1.25d, readBytes(upack.write(Float64(1.25d))).float64, 0.0d)
    assertEquals(3.5f, readBytes(upack.write(Float32(3.5f))).float32, 0.0f)

    assertBytes(upack.write(Str("hello")), 0xa5, 'h', 'e', 'l', 'l', 'o')
    assertEquals("hello", readBytes(upack.write(Str("hello"))).str)

    val binary: Array[Byte] = Array[Byte](1, 2, 3)
    assertBytes(upack.write(Binary(binary)), 0xc4, 0x03, 0x01, 0x02, 0x03)
    assertArrayEquals(binary, readBytes(upack.write(Binary(binary))).binary)
  }

  @Test
  def writesCompactNumericEncodingsAtBoundaryValues(): Unit = {
    assertBytes(upack.write(Int32(0)), 0x00)
    assertBytes(upack.write(Int32(127)), 0x7f)
    assertBytes(upack.write(Int32(128)), 0xcc, 0x80)
    assertBytes(upack.write(Int32(255)), 0xcc, 0xff)
    assertBytes(upack.write(Int32(256)), 0xd1, 0x01, 0x00)
    assertBytes(upack.write(Int32(32767)), 0xd1, 0x7f, 0xff)
    assertBytes(upack.write(Int32(32768)), 0xcd, 0x80, 0x00)
    assertBytes(upack.write(Int32(65535)), 0xcd, 0xff, 0xff)
    assertBytes(upack.write(Int32(65536)), 0xd2, 0x00, 0x01, 0x00, 0x00)

    assertBytes(upack.write(Int32(-1)), 0xff)
    assertBytes(upack.write(Int32(-32)), 0xe0)
    assertBytes(upack.write(Int32(-33)), 0xd0, 0xdf)
    assertBytes(upack.write(Int32(-128)), 0xd0, 0x80)
    assertBytes(upack.write(Int32(-129)), 0xd1, 0xff, 0x7f)
    assertBytes(upack.write(Int32(-32768)), 0xd1, 0x80, 0x00)
    assertBytes(upack.write(Int32(-32769)), 0xd2, 0xff, 0xff, 0x7f, 0xff)

    assertBytes(upack.write(Int64(4294967295L)), 0xce, 0xff, 0xff, 0xff, 0xff)
    assertBytes(upack.write(Int64(4294967296L)), 0xd3, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00)
    assertBytes(upack.write(UInt64(-1L)), 0xcf, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff)
  }

  @Test
  def roundTripsNestedObjectsArraysAndMutableSelectors(): Unit = {
    val original: Msg = Obj(
      Str("title") -> Str("upack"),
      Str("items") -> Arr(Int32(1), True, Null, Obj(Str("nested") -> Float64(2.5d))),
      Str("payload") -> Binary(Array[Byte](9, 8, 7))
    )

    val bytes: Array[Byte] = upack.writeToByteArray(original)
    val parsed: Msg = readBytes(bytes)

    assertEquals("application/octet-stream", parsed.httpContentType.get)
    assertEquals("upack", Msg.Selector.StringSelector("title").apply(parsed).str)
    assertEquals(1, Msg.Selector.IntSelector(0).apply(parsed.obj(Str("items"))).int32)
    assertTrue(parsed.obj(Str("items")).arr(1).bool)
    assertTrue(parsed.obj(Str("items")).arr(2).isNull)
    assertEquals(2.5d, parsed.obj(Str("items")).arr(3).obj(Str("nested")).float64, 0.0d)
    assertArrayEquals(Array[Byte](9, 8, 7), parsed.obj(Str("payload")).binary)

    Msg.Selector.StringSelector("title").update(parsed, Str("changed"))
    Msg.Selector.IntSelector(1).update(parsed.obj(Str("items")), False)
    assertEquals("changed", parsed.obj(Str("title")).str)
    assertFalse(parsed.obj(Str("items")).arr(1).bool)

    val copied: Msg = upack.copy(parsed)
    assertNotSame(parsed, copied)
    Msg.Selector.StringSelector("title").update(copied, Str("copy"))
    assertEquals("changed", parsed.obj(Str("title")).str)
    assertEquals("copy", copied.obj(Str("title")).str)
  }

  @Test
  def roundTripsMapsWithNonStringKeysAndUsesMsgSelectors(): Unit = {
    val original: Msg = Obj(
      Int32(7) -> Str("seven"),
      False -> Arr(Str("bool-key"), Int64(123L)),
      Null -> Obj(Int32(1) -> True)
    )

    val parsed: Msg = readBytes(upack.write(original))

    assertEquals("seven", parsed.obj(Int32(7)).str)
    assertEquals("bool-key", Msg.Selector.MsgSelector(False).apply(parsed).arr(0).str)
    assertEquals(123L, parsed.obj(False).arr(1).int64)
    assertTrue(parsed.obj(Null).obj(Int32(1)).bool)

    Msg.Selector.MsgSelector(Int32(7)).update(parsed, Str("updated"))
    Msg.Selector.MsgSelector(False).update(parsed, Arr(Int32(1), Int32(2)))
    assertEquals("updated", parsed.obj(Int32(7)).str)
    assertEquals(2, parsed.obj(False).arr(1).int32)
  }

  @Test
  def readsManualMessagePackFromArrayAndInputStream(): Unit = {
    val bytes: Array[Byte] = ints(
      0xde, 0x00, 0x03,
      0xd9, 0x07, 'n', 'u', 'm', 'b', 'e', 'r', 's',
      0xdc, 0x00, 0x03, 0xcc, 0xc8, 0xd0, 0xd8, 0xd1, 0x01, 0x2c,
      0xa4, 'b', 'l', 'o', 'b', 0xc4, 0x04, 0x09, 0x08, 0x07, 0x06,
      0xa3, 'e', 'x', 't', 0xd6, 0x05, 0x01, 0x02, 0x03, 0x04
    )

    upack.validate(Readable.fromByteArray(bytes))

    val arrayReader: MsgPackReader = new MsgPackReader(bytes)
    val parsedFromArray: Msg = arrayReader.parse(Msg)
    assertEquals(bytes.length, arrayReader.getIndex)
    assertManualPayload(parsedFromArray)

    val input: ByteArrayInputStream = new ByteArrayInputStream(bytes)
    val streamReader: InputStreamMsgPackReader = new InputStreamMsgPackReader(input, 4, 8)
    val parsedFromStream: Msg = streamReader.parse(Msg)
    assertManualPayload(parsedFromStream)
  }

  @Test
  def handlesVariableLengthStringBinaryExtensionArrayAndMapEncodings(): Unit = {
    val str8: String = "s" * 32
    assertBytesPrefix(upack.write(Str(str8)), 0xd9, 0x20)
    assertEquals(str8, readBytes(upack.write(Str(str8))).str)

    val str16: String = "x" * 256
    assertBytesPrefix(upack.write(Str(str16)), 0xda, 0x01, 0x00)
    assertEquals(str16, readBytes(upack.write(Str(str16))).str)

    val binary16: Array[Byte] = Array.tabulate[Byte](256)(i => i.toByte)
    assertBytesPrefix(upack.write(Binary(binary16)), 0xc5, 0x01, 0x00)
    assertArrayEquals(binary16, readBytes(upack.write(Binary(binary16))).binary)

    val ext8: Ext = Ext(7.toByte, Array.tabulate[Byte](17)(i => (i + 1).toByte))
    val ext8Bytes: Array[Byte] = upack.write(ext8)
    assertBytesPrefix(ext8Bytes, 0xc7, 0x11, 0x07)
    assertExtEquals(ext8, readBytes(ext8Bytes))

    val ext16: Ext = Ext((-4).toByte, Array.tabulate[Byte](256)(i => (255 - i).toByte))
    val ext16Bytes: Array[Byte] = upack.write(ext16)
    assertBytesPrefix(ext16Bytes, 0xc8, 0x01, 0x00, 0xfc)
    assertExtEquals(ext16, readBytes(ext16Bytes))

    val array16: Arr = Arr((0 until 16).map(i => Int32(i))*)
    assertBytesPrefix(upack.write(array16), 0xdc, 0x00, 0x10)
    assertEquals(16, readBytes(upack.write(array16)).arr.length)

    val entries: Seq[(Msg, Msg)] = (0 until 16).map(i => Str(s"k$i") -> Int32(i))
    val map16: Obj = Obj(entries.head, entries.tail*)
    assertBytesPrefix(upack.write(map16), 0xde, 0x00, 0x10)
    assertEquals(15, readBytes(upack.write(map16)).obj(Str("k15")).int32)
  }

  @Test
  def parsesMultipleTopLevelMessagesFromOneReader(): Unit = {
    val first: Array[Byte] = upack.write(Str("first"))
    val second: Array[Byte] = upack.write(Int32(42))
    val third: Array[Byte] = upack.write(Arr(False, Null))
    val reader: MsgPackReader = new MsgPackReader(first ++ second ++ third)

    assertEquals("first", reader.parse(Msg).str)
    assertEquals(first.length, reader.getIndex)

    assertEquals(42, reader.parse(Msg).int32)
    assertEquals(first.length + second.length, reader.getIndex)

    val parsedThird: Msg = reader.parse(Msg)
    assertFalse(parsedThird.arr(0).bool)
    assertTrue(parsedThird.arr(1).isNull)
    assertEquals(first.length + second.length + third.length, reader.getIndex)
  }

  @Test
  def exposesTypedAccessorsAndReportsInvalidAccesses(): Unit = {
    assertEquals("text", Str("text").str)
    assertEquals(12, Int64(12L).int32)
    assertEquals(12L, Int32(12).int64)
    assertEquals(1.5f, Float64(1.5d).float32, 0.0f)
    assertEquals(1.5d, Float32(1.5f).float64, 0.0d)
    assertTrue(True.bool)
    assertFalse(False.bool)
    assertTrue(Null.isNull)
    assertFalse(Str("not-null").isNull)

    val thrown: Msg.InvalidData = assertThrows(classOf[Msg.InvalidData], () => {
      Int32(1).str
      ()
    })
    assertEquals(Int32(1), thrown.data)
    assertTrue(thrown.msg.contains("Expected"))
  }

  @Test
  def writesToProvidedOutputStreamsAndUsesVisitorEntrypoints(): Unit = {
    val out: ByteArrayOutputStream = new ByteArrayOutputStream()
    val msg: Msg = Arr(Str("stream"), Int32(42), Ext(3.toByte, Array[Byte](1, 2)))

    upack.writeTo(msg, out)
    val written: Array[Byte] = out.toByteArray
    assertEquals("stream", readBytes(written).arr(0).str)
    assertEquals(42, readBytes(written).arr(1).int32)
    assertExtEquals(Ext(3.toByte, Array[Byte](1, 2)), readBytes(written).arr(2))

    val transformed: Msg = upack.transform(Readable.fromByteArray(written), Msg)
    assertEquals("stream", transformed.arr(0).str)

    val directOut: ByteArrayOutputStream = new ByteArrayOutputStream()
    msg.writeBytesTo(directOut)
    assertArrayEquals(written, directOut.toByteArray)
  }

  private def readBytes(bytes: Array[Byte]): Msg = {
    upack.read(Readable.fromByteArray(bytes))
  }

  private def assertManualPayload(msg: Msg): Unit = {
    val numbers: collection.mutable.ArrayBuffer[Msg] = msg.obj(Str("numbers")).arr
    assertEquals(3, numbers.length)
    assertEquals(200, numbers(0).int32)
    assertEquals(-40, numbers(1).int32)
    assertEquals(300, numbers(2).int32)
    assertArrayEquals(Array[Byte](9, 8, 7, 6), msg.obj(Str("blob")).binary)
    assertExtEquals(Ext(5.toByte, Array[Byte](1, 2, 3, 4)), msg.obj(Str("ext")))
  }

  private def assertExtEquals(expected: Ext, actual: Msg): Unit = {
    val actualExt: Ext = actual.asInstanceOf[Ext]
    assertEquals(expected.tag, actualExt.tag)
    assertArrayEquals(expected.data, actualExt.data)
  }

  private def assertBytes(actual: Array[Byte], expected: Any*): Unit = {
    assertArrayEquals(ints(expected*), actual)
  }

  private def assertBytesPrefix(actual: Array[Byte], expectedPrefix: Any*): Unit = {
    assertArrayEquals(ints(expectedPrefix*), actual.take(expectedPrefix.length))
  }

  private def ints(values: Any*): Array[Byte] = {
    values.map {
      case value: Byte => value
      case value: Char => value.toInt.toByte
      case value: Int => value.toByte
      case value => throw new IllegalArgumentException(s"Unsupported byte literal: $value")
    }.toArray
  }
}
