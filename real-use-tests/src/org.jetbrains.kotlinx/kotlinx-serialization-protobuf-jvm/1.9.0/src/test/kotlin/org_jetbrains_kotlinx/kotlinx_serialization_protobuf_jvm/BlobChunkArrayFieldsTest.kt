/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_protobuf_jvm

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import kotlinx.serialization.protobuf.ProtoPacked
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Binary and array-typed fields: `ByteArray` as protobuf `bytes`, primitive arrays, `Array` and `Set` as repeated fields.
 *
 * A blob store splits uploaded files into chunks whose raw payload travels as a protobuf `bytes`
 * field, and keeps a manifest per blob that lists chunk sizes, chunk offsets, replica regions and
 * labels. The store writes chunks and manifests to bytes, reassembles a file from decoded chunks,
 * and checks the wire layout other protobuf readers of the same messages rely on.
 */
@OptIn(ExperimentalSerializationApi::class)
class BlobChunkArrayFieldsTest {

    @Serializable
    class Chunk(
        @ProtoNumber(1) val blobId: String,
        @ProtoNumber(2) val sequence: Int,
        @ProtoNumber(3) val payload: ByteArray,
    )

    @Serializable
    class BlobManifest(
        @ProtoNumber(1) val blobId: String,
        @ProtoNumber(2) val chunkSizes: IntArray,
        @ProtoNumber(3) @ProtoPacked val chunkOffsets: LongArray,
        @ProtoNumber(4) val replicas: Array<String>,
        @ProtoNumber(5) val labels: Set<String>,
    )

    /** Splits `content` into chunks of at most `chunkSize` bytes, encoded one message per chunk. */
    private fun upload(blobId: String, content: ByteArray, chunkSize: Int): List<ByteArray> =
        (content.indices step chunkSize).mapIndexed { sequence: Int, start: Int ->
            val end: Int = minOf(start + chunkSize, content.size)
            ProtoBuf.encodeToByteArray(Chunk(blobId, sequence, content.copyOfRange(start, end)))
        }

    @Test
    fun chunkPayloadTravelsVerbatimAsLengthDelimitedBytesField() {
        val payload: ByteArray = byteArrayOf(0x00, 0xFF.toByte(), 0x7F, 0x80.toByte())

        val bytes: ByteArray = ProtoBuf.encodeToByteArray(Chunk("b1", 3, payload))

        val expected: ByteArray = byteArrayOf(0x0A, 0x02) + "b1".encodeToByteArray() +
            byteArrayOf(0x10, 0x03, 0x1A, 0x04) + payload
        assertArrayEquals(expected, bytes)
        val decoded: Chunk = ProtoBuf.decodeFromByteArray(bytes)
        assertEquals("b1", decoded.blobId)
        assertEquals(3, decoded.sequence)
        assertArrayEquals(payload, decoded.payload)
    }

    @Test
    fun fileIsReassembledFromDecodedChunksInSequenceOrder() {
        val content: ByteArray = ByteArray(1000) { index: Int -> (index * 31 % 256).toByte() }

        val stored: List<ByteArray> = upload("report.pdf", content, chunkSize = 256)
        val chunks: List<Chunk> = stored.reversed().map { bytes: ByteArray -> ProtoBuf.decodeFromByteArray<Chunk>(bytes) }
        val reassembled: ByteArray = chunks.sortedBy { chunk: Chunk -> chunk.sequence }
            .fold(ByteArray(0)) { acc: ByteArray, chunk: Chunk -> acc + chunk.payload }

        assertEquals(4, stored.size)
        assertEquals(listOf(256, 256, 256, 232), chunks.sortedBy { it.sequence }.map { it.payload.size })
        assertTrue(chunks.all { chunk: Chunk -> chunk.blobId == "report.pdf" })
        assertArrayEquals(content, reassembled)
    }

    @Test
    fun manifestArraysAndSetEncodeAsRepeatedFields() {
        val manifest = BlobManifest(
            blobId = "b1",
            chunkSizes = intArrayOf(4, 4, 2),
            chunkOffsets = longArrayOf(0L, 4L, 8L),
            replicas = arrayOf("eu", "us"),
            labels = linkedSetOf("hot", "pii"),
        )

        val bytes: ByteArray = ProtoBuf.encodeToByteArray(manifest)

        val expected: ByteArray = byteArrayOf(0x0A, 0x02) + "b1".encodeToByteArray() +
            byteArrayOf(0x10, 0x04, 0x10, 0x04, 0x10, 0x02) +
            byteArrayOf(0x1A, 0x03, 0x00, 0x04, 0x08) +
            byteArrayOf(0x22, 0x02) + "eu".encodeToByteArray() +
            byteArrayOf(0x22, 0x02) + "us".encodeToByteArray() +
            byteArrayOf(0x2A, 0x03) + "hot".encodeToByteArray() +
            byteArrayOf(0x2A, 0x03) + "pii".encodeToByteArray()
        assertArrayEquals(expected, bytes)
        val decoded: BlobManifest = ProtoBuf.decodeFromByteArray(bytes)
        assertEquals("b1", decoded.blobId)
        assertArrayEquals(intArrayOf(4, 4, 2), decoded.chunkSizes)
        assertArrayEquals(longArrayOf(0L, 4L, 8L), decoded.chunkOffsets)
        assertArrayEquals(arrayOf("eu", "us"), decoded.replicas)
        assertEquals(listOf("hot", "pii"), decoded.labels.toList())
    }

    @Test
    fun repeatedLabelsFromAnotherWriterCollapseIntoTheSet() {
        val fromOtherWriter: ByteArray = byteArrayOf(0x0A, 0x02) + "b2".encodeToByteArray() +
            byteArrayOf(0x2A, 0x03) + "hot".encodeToByteArray() +
            byteArrayOf(0x2A, 0x04) + "cold".encodeToByteArray() +
            byteArrayOf(0x2A, 0x03) + "hot".encodeToByteArray()

        val decoded: BlobManifest = ProtoBuf.decodeFromByteArray(fromOtherWriter)

        assertEquals("b2", decoded.blobId)
        assertEquals(setOf("hot", "cold"), decoded.labels)
        assertEquals(0, decoded.chunkSizes.size)
        assertEquals(0, decoded.chunkOffsets.size)
        assertEquals(0, decoded.replicas.size)
    }

    @Test
    fun emptyManifestOfFreshBlobRoundTripsWithEmptyCollections() {
        val fresh = BlobManifest("b3", IntArray(0), LongArray(0), emptyArray(), emptySet())

        val bytes: ByteArray = ProtoBuf.encodeToByteArray(fresh)
        val decoded: BlobManifest = ProtoBuf.decodeFromByteArray(bytes)

        assertArrayEquals(byteArrayOf(0x0A, 0x02) + "b3".encodeToByteArray(), bytes)
        assertEquals("b3", decoded.blobId)
        assertEquals(0, decoded.chunkSizes.size)
        assertEquals(0, decoded.chunkOffsets.size)
        assertEquals(0, decoded.replicas.size)
        assertTrue(decoded.labels.isEmpty())
    }
}
