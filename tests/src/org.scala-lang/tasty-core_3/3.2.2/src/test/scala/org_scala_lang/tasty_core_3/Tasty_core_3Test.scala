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
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

import java.nio.charset.StandardCharsets.US_ASCII
import java.util.Arrays
import java.util.UUID
import scala.jdk.CollectionConverters.*

class Tasty_core_3Test {
  @Test
  def bufferGrowsAndPreservesRawBytes(): Unit = {
    val buffer: TastyBuffer = new TastyBuffer(2)
    val payload: Array[Byte] = Array[Byte](1, 2, 3, 4, 5)

    buffer.writeBytes(payload, payload.length)
    buffer.writeByte(42)
    buffer.assemble()

    assertThat(buffer.length).isEqualTo(6)
    assertThat(buffer.bytes.length).isGreaterThanOrEqualTo(6)
    assertThat(buffer.getByte(Addr(0))).isEqualTo(1)
    assertThat(buffer.getByte(Addr(5))).isEqualTo(42)
    assertThat(trimmedBytes(buffer)).containsExactly(1.toByte, 2.toByte, 3.toByte, 4.toByte, 5.toByte, 42.toByte)
  }

  @Test
  def naturalNumbersRoundTripThroughBufferAndReader(): Unit = {
    val values: Seq[Int] = Seq(0, 1, 2, 63, 64, 127, 128, 16383, 16384, 2097151, Int.MaxValue)
    val buffer: TastyBuffer = new TastyBuffer(1)

    val addresses: Seq[Addr] = values.map { value =>
      val address: Addr = buffer.currentAddr
      buffer.writeNat(value)
      address
    }

    values.zip(addresses).foreach { case (value: Int, address: Addr) =>
      assertThat(buffer.getNat(address)).isEqualTo(value)
      assertThat(buffer.skipNat(address).index - address.index).isEqualTo(TastyBuffer.natSize(value))
    }

    val reader: TastyReader = new TastyReader(trimmedBytes(buffer))
    values.foreach { value =>
      assertThat(reader.readNat()).isEqualTo(value)
    }
    assertThat(reader.isAtEnd).isTrue()
  }

  @Test
  def longNaturalNumbersRoundTripThroughBufferAndReader(): Unit = {
    val values: Seq[Long] = Seq(0L, 1L, 127L, 128L, Int.MaxValue.toLong + 1L, 1099511627776L, Long.MaxValue)
    val buffer: TastyBuffer = new TastyBuffer(1)

    val addresses: Seq[Addr] = values.map { value =>
      val address: Addr = buffer.currentAddr
      buffer.writeLongNat(value)
      address
    }

    values.zip(addresses).foreach { case (value: Long, address: Addr) =>
      assertThat(buffer.getLongNat(address)).isEqualTo(value)
    }

    val reader: TastyReader = new TastyReader(trimmedBytes(buffer))
    values.foreach { value =>
      assertThat(reader.readLongNat()).isEqualTo(value)
    }
    assertThat(reader.isAtEnd).isTrue()
  }

  @Test
  def signedAndUncompressedLongsRoundTripThroughReader(): Unit = {
    val signedValues: Seq[Long] = Seq(-1234567890123L, -8192L, -65L, -64L, -1L, 0L, 1L, 63L, 64L, 8192L, 1234567890123L)
    val fixedWidthValues: Seq[Long] = Seq(0L, 1L, 0x0102030405060708L, -1L)
    val buffer: TastyBuffer = new TastyBuffer(1)

    signedValues.foreach(buffer.writeLongInt)
    fixedWidthValues.foreach(buffer.writeUncompressedLong)

    val reader: TastyReader = new TastyReader(trimmedBytes(buffer))
    signedValues.foreach { value =>
      assertThat(reader.readLongInt()).isEqualTo(value)
    }
    fixedWidthValues.foreach { value =>
      assertThat(reader.readUncompressedLong()).isEqualTo(value)
    }
    assertThat(reader.isAtEnd).isTrue()
  }

  @Test
  def addressesAndReferencesRoundTripWithPatching(): Unit = {
    val buffer: TastyBuffer = new TastyBuffer(32)
    buffer.writeByte(7)
    val reservedAddress: Addr = buffer.reserveAddr()
    buffer.writeNat(99)
    val targetAddress: Addr = buffer.currentAddr
    buffer.writeAddr(targetAddress)
    buffer.fillAddr(reservedAddress, targetAddress)

    assertThat(buffer.getAddr(reservedAddress)).isEqualTo(targetAddress)

    val reader: TastyReader = new TastyReader(trimmedBytes(buffer))
    assertThat(reader.readByte()).isEqualTo(7)
    assertThat(reader.readAddr()).isEqualTo(targetAddress)
    assertThat(reader.readNameRef().index).isEqualTo(99)
    assertThat(reader.readAddr()).isEqualTo(targetAddress)
    assertThat(reader.isAtEnd).isTrue()
  }

  @Test
  def readerSupportsSubReadersAndBoundedCollection(): Unit = {
    val bytes: Array[Byte] = Array[Byte](10, 11, 12, 0, 20, 21)
    val reader: TastyReader = new TastyReader(bytes)

    val firstSegment: List[Int] = reader.until(Addr(3))(reader.readByte())
    assertThat(firstSegment.asJava).containsExactly(10, 11, 12)
    assertThat(reader.ifBefore(Addr(3))("unexpected", "finished")).isEqualTo("finished")

    reader.goto(Addr(0))
    val collected: List[Int] = reader.collectWhile(!reader.isAtEnd && reader.nextByte != 0)(reader.readByte())
    assertThat(collected.asJava).containsExactly(10, 11, 12)
    assertThat(reader.nextByte).isEqualTo(0)

    val subReader: TastyReader = reader.subReader(Addr(4), Addr(6))
    assertThat(subReader.startAddr).isEqualTo(Addr(4))
    assertThat(subReader.endAddr).isEqualTo(Addr(6))
    assertThat(subReader.readBytes(2)).containsExactly(20.toByte, 21.toByte)
    assertThat(subReader.isAtEnd).isTrue()
  }

  @Test
  def readerComputesLengthDelimitedEndAddressesWithLogicalBase(): Unit = {
    val bytes: Array[Byte] = Array[Byte](0, 0, (3 | 0x80).toByte, 10, 11, 12, 99)
    val reader: TastyReader = new TastyReader(bytes, 2, bytes.length, 2)

    val payloadEnd: Addr = reader.readEnd()

    assertThat(reader.startAddr).isEqualTo(Addr(0))
    assertThat(reader.currentAddr).isEqualTo(Addr(1))
    assertThat(payloadEnd).isEqualTo(Addr(4))

    val payload: List[Int] = reader.until(payloadEnd)(reader.readByte())
    assertThat(payload.asJava).containsExactly(10, 11, 12)
    assertThat(reader.currentAddr).isEqualTo(payloadEnd)
    assertThat(reader.readByte()).isEqualTo(99)
    assertThat(reader.isAtEnd).isTrue()
  }

  @Test
  def validHeaderCanBeReadAsUuidOnlyOrFullProduct(): Unit = {
    val uuid: UUID = new UUID(0x0123456789ABCDEFL, 0x0FEDCBA987654321L)
    val toolingVersion: String = "scala-test-tool"
    val bytes: Array[Byte] = headerBytes(uuid, toolingVersion)

    assertThat(new TastyHeaderUnpickler(bytes).readHeader()).isEqualTo(uuid)

    val unpickler: TastyHeaderUnpickler = new TastyHeaderUnpickler(bytes)
    val header = unpickler.readFullHeader()

    assertThat(header.uuid).isEqualTo(uuid)
    assertThat(header.majorVersion).isEqualTo(TastyFormat.MajorVersion)
    assertThat(header.minorVersion).isEqualTo(TastyFormat.MinorVersion)
    assertThat(header.experimentalVersion).isEqualTo(TastyFormat.ExperimentalVersion)
    assertThat(header.toolingVersion).isEqualTo(toolingVersion)
    assertThat(header.productArity).isEqualTo(5)
    assertThat(header.productElementName(0)).isEqualTo("uuid")
    assertThat(header.productElementName(4)).isEqualTo("toolingVersion")
    assertThat(header.productElement(4)).isEqualTo(toolingVersion)
    assertThat(unpickler.isAtEnd).isTrue()
  }

  @Test
  def invalidHeaderBytesFailWithUnpickleException(): Unit = {
    val bytes: Array[Byte] = headerBytes(new UUID(1L, 2L), "tool")
    bytes(0) = (bytes(0) ^ 0x01).toByte

    assertThatThrownBy(() => new TastyHeaderUnpickler(bytes).readFullHeader())
      .isInstanceOf(classOf[UnpickleException])
  }

  @Test
  def formatClassifiesCommonTreeAndNameTags(): Unit = {
    assertThat(TastyFormat.header).isNotEmpty()
    assertThat(TastyFormat.ASTsSection).isEqualTo("ASTs")
    assertThat(TastyFormat.PositionsSection).isEqualTo("Positions")
    assertThat(TastyFormat.CommentsSection).isEqualTo("Comments")

    assertThat(TastyFormat.isVersionCompatible(
      TastyFormat.MajorVersion,
      TastyFormat.MinorVersion,
      TastyFormat.ExperimentalVersion,
      TastyFormat.MajorVersion,
      TastyFormat.MinorVersion,
      TastyFormat.ExperimentalVersion
    )).isTrue()
    assertThat(TastyFormat.isLegalTag(TastyFormat.IDENT)).isTrue()
    assertThat(TastyFormat.isLegalTag(47)).isFalse()
    assertThat(TastyFormat.isModifierTag(TastyFormat.PRIVATE)).isTrue()
    assertThat(TastyFormat.isParamTag(TastyFormat.PARAM)).isTrue()
    assertThat(TastyFormat.isTypeTreeTag(TastyFormat.IDENTtpt)).isTrue()
    assertThat(TastyFormat.astTagToString(TastyFormat.IDENT)).contains("IDENT")
    assertThat(TastyFormat.nameTagToString(1)).isNotEmpty()
    assertThat(TastyFormat.numRefs(TastyFormat.TERMREFpkg)).isGreaterThanOrEqualTo(0)
  }

  @Test
  def tastyHashIsDeterministicAndContentSensitive(): Unit = {
    val first: Array[Byte] = "same tasty payload".getBytes(US_ASCII)
    val equalContent: Array[Byte] = Arrays.copyOf(first, first.length)
    val different: Array[Byte] = "different tasty payload".getBytes(US_ASCII)

    assertThat(TastyHash.pjwHash64(Array.emptyByteArray)).isEqualTo(0L)
    assertThat(TastyHash.pjwHash64(first)).isEqualTo(TastyHash.pjwHash64(equalContent))
    assertThat(TastyHash.pjwHash64(first)).isNotEqualTo(TastyHash.pjwHash64(different))
  }

  private def trimmedBytes(buffer: TastyBuffer): Array[Byte] = {
    Arrays.copyOf(buffer.bytes, buffer.length)
  }

  private def headerBytes(uuid: UUID, toolingVersion: String): Array[Byte] = {
    val buffer: TastyBuffer = new TastyBuffer(64)
    TastyFormat.header.foreach(buffer.writeByte)
    buffer.writeNat(TastyFormat.MajorVersion)
    buffer.writeNat(TastyFormat.MinorVersion)
    buffer.writeNat(TastyFormat.ExperimentalVersion)

    val toolingBytes: Array[Byte] = toolingVersion.getBytes(US_ASCII)
    buffer.writeNat(toolingBytes.length)
    buffer.writeBytes(toolingBytes, toolingBytes.length)
    buffer.writeUncompressedLong(uuid.getMostSignificantBits)
    buffer.writeUncompressedLong(uuid.getLeastSignificantBits)
    trimmedBytes(buffer)
  }
}
