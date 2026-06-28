/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.tasty_core_3

import dotty.tools.tasty.TastyBuffer
import dotty.tools.tasty.TastyBuffer.Addr
import dotty.tools.tasty.TastyFormat
import dotty.tools.tasty.TastyHash
import dotty.tools.tasty.TastyHeaderUnpickler
import dotty.tools.tasty.TastyReader
import dotty.tools.tasty.UnpickleException
import java.nio.charset.StandardCharsets
import java.util.Arrays
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Tasty_core_3Test {
  @Test
  def bufferRoundTripsVariableLengthNumbersAndRawBytes(): Unit = {
    val naturalNumbers: Seq[Int] = Seq(0, 1, 2, 63, 64, 127, 128, 129, 16383, 16384, Int.MaxValue)
    val signedInts: Seq[Int] = Seq(Int.MinValue, -65536, -129, -128, -1, 0, 1, 127, 128, 65535, Int.MaxValue)
    val naturalLongs: Seq[Long] = Seq(0L, 1L, 127L, 128L, 16384L, 4294967295L, Long.MaxValue)
    val signedLongs: Seq[Long] = Seq(Long.MinValue, -9876543210L, -129L, -1L, 0L, 1L, 128L, Long.MaxValue)
    val uncompressedLongs: Seq[Long] = Seq(0L, 0x0102030405060708L, -1L, Long.MinValue, Long.MaxValue)
    val rawBytes: Array[Byte] = Array[Byte](0, 1, 2, 3, 127, -128, -1)

    val buffer = new TastyBuffer(1)
    naturalNumbers.foreach(buffer.writeNat)
    signedInts.foreach(buffer.writeInt)
    naturalLongs.foreach(buffer.writeLongNat)
    signedLongs.foreach(buffer.writeLongInt)
    uncompressedLongs.foreach(buffer.writeUncompressedLong)
    buffer.writeBytes(rawBytes, rawBytes.length)

    assertEquals(1, TastyBuffer.natSize(0))
    assertEquals(1, TastyBuffer.natSize(127))
    assertEquals(2, TastyBuffer.natSize(128))
    assertEquals(2, TastyBuffer.natSize(16383))
    assertEquals(3, TastyBuffer.natSize(16384))

    val reader = new TastyReader(writtenBytes(buffer))
    naturalNumbers.foreach(value => assertEquals(value, reader.readNat()))
    signedInts.foreach(value => assertEquals(value, reader.readInt()))
    naturalLongs.foreach(value => assertEquals(value, reader.readLongNat()))
    signedLongs.foreach(value => assertEquals(value, reader.readLongInt()))
    uncompressedLongs.foreach(value => assertEquals(value, reader.readUncompressedLong()))
    assertArrayEquals(rawBytes, reader.readBytes(rawBytes.length))
    assertTrue(reader.isAtEnd)
  }

  @Test
  def bufferPatchesAddressesAndNavigatesEncodedFields(): Unit = {
    val buffer = new TastyBuffer(32)
    buffer.writeByte(0)
    buffer.writeByte(0)
    buffer.writeByte(5)
    assertEquals(Addr(2), buffer.skipZeroes(Addr(0)))

    val natStart = buffer.currentAddr
    buffer.writeNat(16384)
    assertEquals(buffer.currentAddr, buffer.skipNat(natStart))

    val slot = buffer.reserveAddr()
    buffer.writeByte(99)
    val target = buffer.currentAddr
    buffer.writeNat(321)
    buffer.fillAddr(slot, target)
    buffer.writeAddr(Addr(7))

    assertEquals(target, buffer.getAddr(slot))
    assertEquals(321, buffer.getNat(target))

    val reader = new TastyReader(writtenBytes(buffer))
    reader.goto(slot)
    assertEquals(target, reader.readAddr())
    assertEquals(99, reader.readByte())
    assertEquals(321, reader.readNat())
    assertEquals(Addr(7), reader.readAddr())

    val base = Addr(10)
    val addr = Addr(20)
    assertEquals(Addr(25), addr + 5)
    assertEquals(Addr(17), addr - 3)
    assertEquals(Addr(6), addr.relativeTo(base))
    assertTrue(addr == Addr(20))
    assertTrue(addr != base)
    assertEquals(Addr(-1), TastyBuffer.NoAddr)
    assertEquals(4, TastyBuffer.AddrWidth)
  }

  @Test
  def readerSupportsSubReadersLengthDelimitedEndsAndCollectionHelpers(): Unit = {
    val lengthDelimited = new TastyBuffer(8)
    lengthDelimited.writeNat(3)
    lengthDelimited.writeByte(10)
    lengthDelimited.writeByte(11)
    lengthDelimited.writeByte(12)
    lengthDelimited.writeByte(99)

    val reader = new TastyReader(writtenBytes(lengthDelimited))
    val end = reader.readEnd()
    assertEquals(Addr(4), end)
    assertEquals(List(10, 11, 12), reader.until(end)(reader.readByte()))
    assertEquals(99, reader.ifBefore(reader.endAddr)(reader.readByte(), -1))
    assertEquals(-1, reader.ifBefore(reader.endAddr)(reader.readByte(), -1))
    assertTrue(reader.isAtEnd)

    val values = Array[Byte](1, 2, 3, 4, 5, 6)
    val basedReader = new TastyReader(values, 2, 6, 2)
    assertEquals(Addr(0), basedReader.startAddr)
    assertEquals(Addr(4), basedReader.endAddr)
    assertEquals(3, basedReader.nextByte)
    val subReader = basedReader.subReader(Addr(1), Addr(3))
    assertEquals(List(4, 5), subReader.collectWhile(!subReader.isAtEnd)(subReader.readByte()))
    assertTrue(subReader.isAtEnd)
    assertEquals(Addr(0), basedReader.addr(2))
    assertEquals(4, basedReader.index(Addr(2)))
  }

  @Test
  def formatConstantsClassifyTagsAndVersions(): Unit = {
    assertArrayEquals(Array(0x5c, 0xa1, 0xab, 0x1f), TastyFormat.header)
    assertEquals("ASTs", TastyFormat.ASTsSection)
    assertEquals("Positions", TastyFormat.PositionsSection)
    assertEquals("Comments", TastyFormat.CommentsSection)

    assertTrue(TastyFormat.isVersionCompatible(28, 2, 0, 28, 2, 0))
    assertTrue(TastyFormat.isVersionCompatible(28, 1, 0, 28, 2, 0))
    assertTrue(TastyFormat.isVersionCompatible(28, 2, 7, 28, 2, 7))
    assertFalse(TastyFormat.isVersionCompatible(27, 2, 0, 28, 2, 0))
    assertFalse(TastyFormat.isVersionCompatible(28, 3, 0, 28, 2, 0))
    assertFalse(TastyFormat.isVersionCompatible(28, 2, 1, 28, 2, 0))
    assertFalse(TastyFormat.isVersionCompatible(28, 1, 1, 28, 2, 0))

    assertEquals("UTF8", TastyFormat.nameTagToString(TastyFormat.NameTags.UTF8))
    assertEquals("TARGETSIGNED", TastyFormat.nameTagToString(TastyFormat.NameTags.TARGETSIGNED))
    assertEquals("NotANameTag(777)", TastyFormat.nameTagToString(777))

    assertTrue(TastyFormat.isLegalTag(TastyFormat.UNITconst))
    assertTrue(TastyFormat.isLegalTag(TastyFormat.INTconst))
    assertTrue(TastyFormat.isLegalTag(TastyFormat.THIS))
    assertTrue(TastyFormat.isLegalTag(TastyFormat.IDENT))
    assertTrue(TastyFormat.isLegalTag(TastyFormat.PACKAGE))
    assertTrue(TastyFormat.isLegalTag(TastyFormat.HOLE))
    assertFalse(TastyFormat.isLegalTag(1))
    assertFalse(TastyFormat.isLegalTag(50))

    assertTrue(TastyFormat.isParamTag(TastyFormat.PARAM))
    assertTrue(TastyFormat.isParamTag(TastyFormat.TYPEPARAM))
    assertFalse(TastyFormat.isParamTag(TastyFormat.DEFDEF))

    assertTrue(TastyFormat.isModifierTag(TastyFormat.FINAL))
    assertTrue(TastyFormat.isModifierTag(TastyFormat.ANNOTATION))
    assertTrue(TastyFormat.isModifierTag(TastyFormat.PRIVATEqualified))
    assertFalse(TastyFormat.isModifierTag(TastyFormat.DEFDEF))

    assertTrue(TastyFormat.isTypeTreeTag(TastyFormat.IDENTtpt))
    assertTrue(TastyFormat.isTypeTreeTag(TastyFormat.APPLIEDtpt))
    assertTrue(TastyFormat.isTypeTreeTag(TastyFormat.MATCHtpt))
    assertTrue(TastyFormat.isTypeTreeTag(TastyFormat.BIND))
    assertFalse(TastyFormat.isTypeTreeTag(TastyFormat.IDENT))

    assertEquals("UNITconst", TastyFormat.astTagToString(TastyFormat.UNITconst))
    assertEquals("TYPELAMBDAtype", TastyFormat.astTagToString(TastyFormat.TYPELAMBDAtype))
    assertEquals("HOLE", TastyFormat.astTagToString(TastyFormat.HOLE))

    assertEquals(1, TastyFormat.numRefs(TastyFormat.VALDEF))
    assertEquals(2, TastyFormat.numRefs(TastyFormat.RENAMED))
    assertEquals(-1, TastyFormat.numRefs(TastyFormat.METHODtype))
    assertEquals(0, TastyFormat.numRefs(TastyFormat.APPLY))
  }

  @Test
  def headerUnpicklerReadsValidHeadersFromBytesAndReader(): Unit = {
    val uuid = new UUID(0x0123456789abcdefL, -81985529216486896L)
    val bytes = tastyHeaderBytes(
      TastyFormat.MajorVersion,
      TastyFormat.MinorVersion,
      TastyFormat.ExperimentalVersion,
      "scala-test-tool",
      uuid
    )

    val fromBytes = new TastyHeaderUnpickler(bytes)
    val fullHeader = fromBytes.readFullHeader()
    assertTrue(fromBytes.isAtEnd)
    assertEquals(uuid, fullHeader.uuid)
    assertEquals(TastyFormat.MajorVersion, fullHeader.majorVersion)
    assertEquals(TastyFormat.MinorVersion, fullHeader.minorVersion)
    assertEquals(TastyFormat.ExperimentalVersion, fullHeader.experimentalVersion)
    assertEquals("scala-test-tool", fullHeader.toolingVersion)
    assertEquals(uuid, fullHeader._1)
    assertEquals(TastyFormat.MajorVersion, fullHeader._2)
    assertEquals(TastyFormat.MinorVersion, fullHeader._3)
    assertEquals(TastyFormat.ExperimentalVersion, fullHeader._4)
    assertEquals("scala-test-tool", fullHeader._5)
    assertEquals(5, fullHeader.productArity)
    assertEquals("TastyHeader", fullHeader.productPrefix)
    assertEquals("uuid", fullHeader.productElementName(0))
    assertEquals(uuid, fullHeader.productElement(0))
    assertTrue(fullHeader.canEqual(fullHeader))
    assertEquals(fullHeader, fullHeader)
    assertEquals(fullHeader.hashCode(), fullHeader.hashCode())
    assertTrue(fullHeader.toString.contains("scala-test-tool"))

    val fromReader = new TastyHeaderUnpickler(new TastyReader(bytes))
    assertEquals(uuid, fromReader.readHeader())
    assertTrue(fromReader.isAtEnd)
  }

  @Test
  def headerUnpicklerRejectsInvalidMagicAndIncompatibleVersions(): Unit = {
    val uuid = new UUID(1L, 2L)
    val badMagic = tastyHeaderBytes(
      TastyFormat.MajorVersion,
      TastyFormat.MinorVersion,
      TastyFormat.ExperimentalVersion,
      "bad-magic",
      uuid
    )
    badMagic(0) = 0

    val badMagicError = assertThrows(classOf[UnpickleException], () => new TastyHeaderUnpickler(badMagic).readFullHeader())
    assertTrue(badMagicError.getMessage.contains("not a TASTy file"))

    val futureMinor = tastyHeaderBytes(
      TastyFormat.MajorVersion,
      TastyFormat.MinorVersion + 1,
      0,
      "future-tool",
      uuid
    )
    val futureMinorError = assertThrows(classOf[UnpickleException], () => new TastyHeaderUnpickler(futureMinor).readHeader())
    assertTrue(futureMinorError.getMessage.nonEmpty)

    val oldMajor = tastyHeaderBytes(27, 0, 0, "old-tool", uuid)
    val oldMajorError = assertThrows(classOf[UnpickleException], () => new TastyHeaderUnpickler(oldMajor).readHeader())
    assertTrue(oldMajorError.getMessage.nonEmpty)
  }

  @Test
  def hashUsesUnsignedBytesAndProducesStablePjwValues(): Unit = {
    assertEquals(0L, TastyHash.pjwHash64(Array.emptyByteArray))
    assertEquals(361873233017L, TastyHash.pjwHash64("TASTy".getBytes(StandardCharsets.UTF_8)))
    assertEquals(4336877823L, TastyHash.pjwHash64(Array[Byte](0, 1, 2, 127, -128, -1)))
  }

  private def tastyHeaderBytes(
      majorVersion: Int,
      minorVersion: Int,
      experimentalVersion: Int,
      toolingVersion: String,
      uuid: UUID
  ): Array[Byte] = {
    val buffer = new TastyBuffer(64)
    TastyFormat.header.foreach(buffer.writeByte)
    buffer.writeNat(majorVersion)
    buffer.writeNat(minorVersion)
    buffer.writeNat(experimentalVersion)
    val toolingBytes = toolingVersion.getBytes(StandardCharsets.UTF_8)
    buffer.writeNat(toolingBytes.length)
    buffer.writeBytes(toolingBytes, toolingBytes.length)
    buffer.writeUncompressedLong(uuid.getMostSignificantBits)
    buffer.writeUncompressedLong(uuid.getLeastSignificantBits)
    writtenBytes(buffer)
  }

  private def writtenBytes(buffer: TastyBuffer): Array[Byte] =
    Arrays.copyOf(buffer.bytes, buffer.length)
}
