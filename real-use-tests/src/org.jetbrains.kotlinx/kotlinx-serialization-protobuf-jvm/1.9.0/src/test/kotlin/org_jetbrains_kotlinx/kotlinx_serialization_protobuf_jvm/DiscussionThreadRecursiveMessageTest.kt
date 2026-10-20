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
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Recursive message types: self-referencing `@Serializable` classes nested as length-delimited sub-messages.
 *
 * A discussion board stores each thread as one protobuf message: a comment holds its replies as a
 * repeated field of the same message type, and the moderation log chains every revision to the
 * revision it replaced through an optional field of its own type. The board writes whole trees and
 * long chains to bytes, reads them back, and walks them the way its pages render them.
 */
@OptIn(ExperimentalSerializationApi::class)
class DiscussionThreadRecursiveMessageTest {

    @Serializable
    data class Comment(
        @ProtoNumber(1) val id: Int,
        @ProtoNumber(2) val author: String = "",
        @ProtoNumber(3) val body: String = "",
        @ProtoNumber(4) val replies: List<Comment> = emptyList(),
    ) {
        fun size(): Int = 1 + replies.sumOf { it.size() }

        fun depth(): Int = 1 + (replies.maxOfOrNull { it.depth() } ?: 0)

        fun authors(): Set<String> = setOf(author) + replies.flatMap { it.authors() }
    }

    @Serializable
    data class Revision(
        @ProtoNumber(1) val number: Int,
        @ProtoNumber(2) val text: String,
        @ProtoNumber(3) val previous: Revision? = null,
    ) {
        fun history(): List<Int> = generateSequence(this) { it.previous }.map { it.number }.toList()
    }

    private val protoBuf: ProtoBuf = ProtoBuf

    @Test
    fun storesAndRestoresNestedReplyTree() {
        val thread = Comment(
            id = 1,
            author = "ada",
            body = "Which codec should the sync service use?",
            replies = listOf(
                Comment(
                    id = 2,
                    author = "bo",
                    body = "Protobuf, the payloads are small.",
                    replies = listOf(
                        Comment(id = 4, author = "ada", body = "Even for nested trees?"),
                        Comment(
                            id = 5,
                            author = "cy",
                            body = "Yes, each level is a sub-message.",
                            replies = listOf(Comment(id = 7, author = "bo", body = "Agreed.")),
                        ),
                    ),
                ),
                Comment(id = 3, author = "dee", body = "JSON is easier to debug."),
            ),
        )

        val stored: ByteArray = protoBuf.encodeToByteArray(thread)
        val restored: Comment = protoBuf.decodeFromByteArray(stored)

        assertEquals(thread, restored)
        assertEquals(6, restored.size())
        assertEquals(4, restored.depth())
        assertEquals(setOf("ada", "bo", "cy", "dee"), restored.authors())
        assertEquals(listOf(2, 3), restored.replies.map { it.id })
        assertEquals("Agreed.", restored.replies[0].replies[1].replies[0].body)
    }

    @Test
    fun writesEachReplyAsLengthDelimitedSubMessage() {
        val thread = Comment(id = 1, replies = listOf(Comment(id = 2, replies = listOf(Comment(id = 3)))))

        val bytes: ByteArray = protoBuf.encodeToByteArray(thread)

        // Field 1 = 1, then field 4 (wire type 2) holding six bytes: field 1 = 2 and field 4
        // holding two bytes: field 1 = 3. Empty strings and empty reply lists are not written.
        assertArrayEquals(
            byteArrayOf(0x08, 0x01, 0x22, 0x06, 0x08, 0x02, 0x22, 0x02, 0x08, 0x03),
            bytes,
        )
        assertEquals(thread, protoBuf.decodeFromByteArray<Comment>(bytes))
    }

    @Test
    fun restoresLongRevisionChain() {
        val latest: Revision = (1..200).fold(null as Revision?) { previous, number ->
            Revision(number = number, text = "revision $number", previous = previous)
        }!!

        val restored: Revision = protoBuf.decodeFromByteArray(protoBuf.encodeToByteArray(latest))

        assertEquals(latest, restored)
        assertEquals((200 downTo 1).toList(), restored.history())
        val first: Revision = generateSequence(restored) { it.previous }.last()
        assertEquals("revision 1", first.text)
        assertNull(first.previous)
    }

    @Test
    fun decodesLeafWithoutRecursiveFields() {
        val leaf = Revision(number = 1, text = "draft")

        val bytes: ByteArray = protoBuf.encodeToByteArray(leaf)
        val restored: Revision = protoBuf.decodeFromByteArray(bytes)

        // Field 1 = 1, then field 2 holding "draft"; the absent predecessor writes nothing.
        assertEquals(2 + 2 + "draft".length, bytes.size)
        assertNull(restored.previous)
        assertEquals(listOf(1), restored.history())
    }
}
