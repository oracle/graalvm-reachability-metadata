/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_io_bytestring_jvm

import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.ByteStringBuilder
import kotlinx.io.bytestring.append
import kotlinx.io.bytestring.asReadOnlyByteBuffer
import kotlinx.io.bytestring.buildByteString
import kotlinx.io.bytestring.contentEquals
import kotlinx.io.bytestring.decode
import kotlinx.io.bytestring.decodeIntoByteArray
import kotlinx.io.bytestring.decodeToByteString
import kotlinx.io.bytestring.decodeToString
import kotlinx.io.bytestring.encode
import kotlinx.io.bytestring.encodeIntoByteArray
import kotlinx.io.bytestring.encodeToAppendable
import kotlinx.io.bytestring.encodeToByteArray
import kotlinx.io.bytestring.encodeToByteString
import kotlinx.io.bytestring.endsWith
import kotlinx.io.bytestring.getByteString
import kotlinx.io.bytestring.hexToByteString
import kotlinx.io.bytestring.indexOf
import kotlinx.io.bytestring.indices
import kotlinx.io.bytestring.isEmpty
import kotlinx.io.bytestring.isNotEmpty
import kotlinx.io.bytestring.lastIndexOf
import kotlinx.io.bytestring.putByteString
import kotlinx.io.bytestring.startsWith
import kotlinx.io.bytestring.toHexString
import kotlinx.io.bytestring.unsafe.UnsafeByteStringApi
import kotlinx.io.bytestring.unsafe.UnsafeByteStringOperations
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ReadOnlyBufferException
import java.nio.charset.StandardCharsets
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.text.HexFormat

@OptIn(
    ExperimentalEncodingApi::class,
    ExperimentalStdlibApi::class,
    ExperimentalUnsignedTypes::class,
    UnsafeByteStringApi::class
)
public class Kotlinx_io_bytestring_jvmTest {
    @Test
    fun constructionCopiesRangesAndExposesImmutableValueOperations() {
        val source: ByteArray = byteArrayOf(9, 1, 2, 3, 4, 8)
        val bytes: ByteString = ByteString(source, startIndex = 1, endIndex = 5)
        source[2] = 99

        assertEquals(4, bytes.size)
        assertEquals(1.toByte(), bytes[0])
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), bytes.toByteArray())
        assertArrayEquals(byteArrayOf(2, 3), bytes.toByteArray(startIndex = 1, endIndex = 3))
        assertEquals(0..3, bytes.indices)
        assertTrue(bytes.isNotEmpty())
        assertFalse(bytes.isEmpty())

        val exported: ByteArray = bytes.toByteArray()
        exported[0] = 42
        assertEquals(1.toByte(), bytes[0], "toByteArray must return a defensive copy")

        val destination: ByteArray = byteArrayOf(-1, -1, -1, -1, -1, -1)
        bytes.copyInto(destination, destinationOffset = 2, startIndex = 1, endIndex = 4)
        assertArrayEquals(byteArrayOf(-1, -1, 2, 3, 4, -1), destination)

        val substring: ByteString = bytes.substring(startIndex = 1, endIndex = 3)
        assertArrayEquals(byteArrayOf(2, 3), substring.toByteArray())
        assertEquals(ByteString(), bytes.substring(startIndex = 2, endIndex = 2))
        assertEquals("ByteString(size=4 hex=01020304)", bytes.toString())
        assertEquals("ByteString(size=0)", ByteString().toString())

        assertThrows(IndexOutOfBoundsException::class.java) { bytes[-1] }
        assertThrows(IllegalArgumentException::class.java) { bytes.toByteArray(startIndex = 3, endIndex = 1) }
        assertThrows(IndexOutOfBoundsException::class.java) { bytes.copyInto(ByteArray(1), startIndex = 0, endIndex = 4) }
    }

    @Test
    fun unsignedByteFactoryPreservesUnsignedValuesAsByteContent() {
        val bytes: ByteString = ByteString(
            0x00u.toUByte(),
            0x7fu.toUByte(),
            0x80u.toUByte(),
            0xffu.toUByte()
        )

        assertEquals(4, bytes.size)
        assertEquals(0x00.toByte(), bytes[0])
        assertEquals(0x7f.toByte(), bytes[1])
        assertEquals(0x80.toByte(), bytes[2])
        assertEquals(0xff.toByte(), bytes[3])
        assertArrayEquals(byteArrayOf(0x00, 0x7f, 0x80.toByte(), 0xff.toByte()), bytes.toByteArray())
    }

    @Test
    fun equalityHashingAndComparisonUseByteContentAndUnsignedOrdering() {
        val first: ByteString = ByteString(1.toByte(), 2.toByte(), 3.toByte())
        val sameContent: ByteString = ByteString(byteArrayOf(1, 2, 3))
        val differentContent: ByteString = ByteString(1.toByte(), 2.toByte(), 4.toByte())

        assertEquals(first, sameContent)
        assertEquals(first.hashCode(), sameContent.hashCode())
        assertNotEquals(first, differentContent)
        assertNotEquals(first, byteArrayOf(1, 2, 3))

        assertTrue(ByteString(0xff.toByte()) > ByteString(0x7f.toByte()))
        assertTrue(ByteString(1.toByte(), 2.toByte()) < ByteString(1.toByte(), 2.toByte(), 0.toByte()))
        assertEquals(0, first.compareTo(sameContent))
    }

    @Test
    fun searchPrefixSuffixAndContentChecksCoverBytesArraysAndByteStrings() {
        val bytes: ByteString = ByteString(
            1.toByte(),
            2.toByte(),
            3.toByte(),
            2.toByte(),
            3.toByte(),
            4.toByte(),
            2.toByte(),
            3.toByte()
        )

        assertEquals(1, bytes.indexOf(2.toByte()))
        assertEquals(3, bytes.indexOf(2.toByte(), startIndex = 2))
        assertEquals(-1, bytes.indexOf(9.toByte()))
        assertEquals(1, bytes.indexOf(ByteString(2.toByte(), 3.toByte())))
        assertEquals(3, bytes.indexOf(ByteString(2.toByte(), 3.toByte()), startIndex = 2))
        assertEquals(6, bytes.indexOf(byteArrayOf(2, 3), startIndex = 5))
        assertEquals(0, bytes.indexOf(ByteString(), startIndex = -20))
        assertEquals(bytes.size, bytes.indexOf(ByteString(), startIndex = 100))

        assertEquals(6, bytes.lastIndexOf(2.toByte()))
        assertEquals(6, bytes.lastIndexOf(ByteString(2.toByte(), 3.toByte())))
        assertEquals(6, bytes.lastIndexOf(byteArrayOf(2, 3), startIndex = 2))
        assertEquals(bytes.size, bytes.lastIndexOf(ByteArray(0)))

        assertTrue(bytes.startsWith(byteArrayOf(1, 2, 3)))
        assertTrue(bytes.startsWith(ByteString(1.toByte(), 2.toByte())))
        assertFalse(bytes.startsWith(byteArrayOf(2, 3)))
        assertTrue(bytes.endsWith(byteArrayOf(4, 2, 3)))
        assertTrue(bytes.endsWith(ByteString(2.toByte(), 3.toByte())))
        assertFalse(bytes.endsWith(ByteString(3.toByte(), 2.toByte())))
        assertTrue(bytes.contentEquals(byteArrayOf(1, 2, 3, 2, 3, 4, 2, 3)))
        assertFalse(bytes.contentEquals(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun builderAppendsSingleBytesArraysUnsignedBytesAndByteStrings() {
        val builder: ByteStringBuilder = ByteStringBuilder(initialCapacity = 2)
        assertEquals(0, builder.size)
        assertEquals(2, builder.capacity)

        builder.append(1.toByte())
        builder.append(byteArrayOf(0, 2, 3, 9), startIndex = 1, endIndex = 3)
        builder.append(4.toUByte())
        builder.append(5.toByte(), 6.toByte())
        builder.append(ByteString(7.toByte(), 8.toByte()))

        val built: ByteString = builder.toByteString()
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8), built.toByteArray())
        assertTrue(builder.capacity >= builder.size)

        val fromDsl: ByteString = buildByteString(capacity = 4) {
            append(ByteString(10.toByte(), 11.toByte()))
            append(byteArrayOf(12, 13, 14), startIndex = 1, endIndex = 3)
        }
        assertArrayEquals(byteArrayOf(10, 11, 13, 14), fromDsl.toByteArray())

        builder.append(9.toByte())
        assertArrayEquals(
            byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8),
            built.toByteArray(),
            "previously built value must remain stable"
        )
        assertThrows(IllegalArgumentException::class.java) {
            ByteStringBuilder().append(byteArrayOf(1, 2), startIndex = 2, endIndex = 1)
        }
        assertThrows(IndexOutOfBoundsException::class.java) {
            ByteStringBuilder().append(byteArrayOf(1, 2), startIndex = -1, endIndex = 1)
        }
    }

    @Test
    fun utf8CharsetAndByteBufferExtensionsRoundTripWithoutMutableExposure() {
        val utf8: ByteString = "Kotlin ∑ bytes".encodeToByteString()
        assertEquals("Kotlin ∑ bytes", utf8.decodeToString())

        val latin1: ByteString = "caf\u00e9".encodeToByteString(StandardCharsets.ISO_8859_1)
        assertArrayEquals(byteArrayOf(99, 97, 102, 0xe9.toByte()), latin1.toByteArray())
        assertEquals("caf\u00e9", latin1.decodeToString(StandardCharsets.ISO_8859_1))

        val readOnly: ByteBuffer = ByteString(10.toByte(), 20.toByte(), 30.toByte()).asReadOnlyByteBuffer()
        val observed: ByteArray = ByteArray(3)
        assertTrue(readOnly.isReadOnly)
        readOnly.get(observed)
        assertArrayEquals(byteArrayOf(10, 20, 30), observed)
        assertThrows(ReadOnlyBufferException::class.java) { readOnly.put(0, 99.toByte()) }

        val sourceBuffer: ByteBuffer = ByteBuffer.wrap(byteArrayOf(9, 8, 7, 6, 5))
        sourceBuffer.position(1)
        val relativeRead: ByteString = sourceBuffer.getByteString(length = 3)
        assertArrayEquals(byteArrayOf(8, 7, 6), relativeRead.toByteArray())
        assertEquals(4, sourceBuffer.position())

        val absoluteRead: ByteString = sourceBuffer.getByteString(2, 2)
        assertArrayEquals(byteArrayOf(7, 6), absoluteRead.toByteArray())
        assertEquals(4, sourceBuffer.position())

        val destinationBuffer: ByteBuffer = ByteBuffer.allocate(8)
        destinationBuffer.putByteString(ByteString(1.toByte(), 2.toByte(), 3.toByte()))
        assertEquals(3, destinationBuffer.position())
        destinationBuffer.putByteString(5, ByteString(4.toByte(), 5.toByte()))
        assertArrayEquals(byteArrayOf(1, 2, 3, 0, 0, 4, 5, 0), destinationBuffer.array())

        assertThrows(IndexOutOfBoundsException::class.java) { ByteBuffer.allocate(2).getByteString(length = 3) }
        assertThrows(IndexOutOfBoundsException::class.java) { ByteBuffer.allocate(2).putByteString(2, ByteString(1.toByte())) }
    }

    @Test
    fun base64AndHexExtensionsEncodeDecodeCompleteAndPartialRanges() {
        val payload: ByteString = "Kotlin\u2713".encodeToByteString()
        assertEquals("S290bGlu4pyT", Base64.Default.encode(payload))
        assertEquals("S290bGlu", Base64.Default.encode(payload, startIndex = 0, endIndex = 6))
        assertEquals("S290bGlu4pyT", Base64.Default.encodeToByteArray(payload).toString(StandardCharsets.US_ASCII))

        val encodedDestination: ByteArray = ByteArray(12) { '.'.code.toByte() }
        val encodedCount: Int = Base64.Default.encodeIntoByteArray(
            payload,
            encodedDestination,
            destinationOffset = 2,
            startIndex = 0,
            endIndex = 6
        )
        assertEquals(8, encodedCount)
        assertEquals("..S290bGlu..", encodedDestination.toString(StandardCharsets.US_ASCII))

        val appendable: StringBuilder = StringBuilder("prefix:")
        assertEquals(appendable, Base64.Default.encodeToAppendable(payload, appendable, startIndex = 0, endIndex = 6))
        assertEquals("prefix:S290bGlu", appendable.toString())

        assertEquals(payload, Base64.Default.decodeToByteString("S290bGlu4pyT"))
        assertEquals(payload, Base64.Default.decodeToByteString("xxS290bGlu4pyTyy", startIndex = 2, endIndex = 14))
        assertEquals(payload, Base64.Default.decodeToByteString("S290bGlu4pyT".toByteArray(StandardCharsets.US_ASCII)))
        assertEquals(payload, Base64.Default.decodeToByteString("S290bGlu4pyT".encodeToByteString()))
        assertArrayEquals("Kotlin".toByteArray(), Base64.Default.decode("S290bGlu".encodeToByteString()))

        val decodedDestination: ByteArray = ByteArray(8) { '.'.code.toByte() }
        val decodedCount: Int = Base64.Default.decodeIntoByteArray(
            "S290bGlu".encodeToByteString(),
            decodedDestination,
            destinationOffset = 1
        )
        assertEquals(6, decodedCount)
        assertArrayEquals(byteArrayOf('.'.code.toByte(), 75, 111, 116, 108, 105, 110, '.'.code.toByte()), decodedDestination)

        val binary: ByteString = ByteString(0x00.toByte(), 0x0f.toByte(), 0x10.toByte(), 0xff.toByte())
        assertEquals("000f10ff", binary.toHexString())
        assertEquals("0f10", binary.toHexString(startIndex = 1, endIndex = 3))
        assertEquals(binary, "000F10ff".hexToByteString())
        assertThrows(IllegalArgumentException::class.java) { "not-hex".hexToByteString() }
    }

    @Test
    fun customHexFormatControlsByteStringEncodingAndDecoding() {
        val bytes: ByteString = ByteString(0x0a.toByte(), 0x1b.toByte(), 0xff.toByte())
        val format: HexFormat = HexFormat {
            upperCase = true
            bytes {
                bytePrefix = "0x"
                byteSeparator = ", "
            }
        }

        assertEquals("0x0A, 0x1B, 0xFF", bytes.toHexString(format))
        assertEquals("0x1B, 0xFF", bytes.toHexString(startIndex = 1, endIndex = 3, format = format))
        assertEquals(bytes, "0x0A, 0x1B, 0xFF".hexToByteString(format))
    }

    @Test
    fun unsafeOperationsExposeBackingStorageOnlyWhenExplicitlyOptedIn() {
        val backing: ByteArray = byteArrayOf(1, 2, 3)
        val wrapped: ByteString = UnsafeByteStringOperations.wrapUnsafe(backing)
        backing[1] = 99
        assertArrayEquals(byteArrayOf(1, 99, 3), wrapped.toByteArray())

        UnsafeByteStringOperations.withByteArrayUnsafe(wrapped) { array: ByteArray ->
            assertTrue(array === backing)
            array[2] = 42
        }
        assertArrayEquals(byteArrayOf(1, 99, 42), wrapped.toByteArray())
    }
}
