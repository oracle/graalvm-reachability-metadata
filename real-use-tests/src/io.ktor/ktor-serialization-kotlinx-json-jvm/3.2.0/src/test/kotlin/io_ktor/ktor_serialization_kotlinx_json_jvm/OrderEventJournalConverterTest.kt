/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_serialization_kotlinx_json_jvm

import io.ktor.http.ContentType
import io.ktor.http.charset
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.withCharset
import io.ktor.serialization.ContentConverter
import io.ktor.serialization.JsonConvertException
import io.ktor.serialization.kotlinx.KotlinxSerializationConverter
import io.ktor.serialization.kotlinx.json.DefaultJson
import io.ktor.serialization.kotlinx.json.ExperimentalJsonConverter
import io.ktor.util.reflect.typeInfo
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.charset.Charset

/**
 * Direct use of the kotlinx JSON content converters outside an HTTP pipeline: an order service
 * calls KotlinxSerializationConverter and ExperimentalJsonConverter itself to write its event
 * journal as byte records and to replay them, including whole-journal Flow exports, lazy
 * Sequence replays, charset-aware records, nullable snapshots and rejection of corrupt records.
 */
public class OrderEventJournalConverterTest {

    /** Everything that can happen to an order, stored with a type discriminator. */
    @Serializable
    private sealed interface OrderEvent {
        val orderId: String
    }

    @Serializable
    @SerialName("placed")
    private data class Placed(override val orderId: String, val customer: String, val totalCents: Long) : OrderEvent

    @Serializable
    @SerialName("shipped")
    private data class Shipped(override val orderId: String, val carrier: String) : OrderEvent

    @Serializable
    @SerialName("cancelled")
    private data class Cancelled(override val orderId: String, val reason: String? = null) : OrderEvent

    /** The latest known state of one order, kept next to the journal. */
    @Serializable
    private data class Snapshot(val orderId: String, val status: String, val events: Int)

    /** An append-only journal of encoded records, written and read through one converter. */
    private class Journal(private val converter: ContentConverter, private val charset: Charset) {
        private val records: MutableList<ByteArray> = mutableListOf()

        val size: Int get() = records.size

        suspend fun append(event: OrderEvent) {
            val content: OutgoingContent = converter.serialize(contentType(), charset, typeInfo<OrderEvent>(), event)
                ?: error("converter refused $event")
            records.add(drain(content))
        }

        suspend fun replay(): List<OrderEvent> = records.map { record ->
            converter.deserialize(charset, typeInfo<OrderEvent>(), ByteReadChannel(record)) as OrderEvent
        }

        fun contentType(): ContentType = ContentType.Application.Json.withCharset(charset)

        fun rawRecord(index: Int): String = String(records[index], charset)
    }

    private val journalJson: Json = Json(DefaultJson) {
        classDiscriminator = "event"
        explicitNulls = false
    }

    @Test
    fun `events are appended as typed JSON records and replayed in order`() = runBlocking {
        val journal = Journal(KotlinxSerializationConverter(journalJson), Charsets.UTF_8)
        journal.append(Placed("ord-1", customer = "Zoe", totalCents = 4_250))
        journal.append(Shipped("ord-1", carrier = "PostNord"))
        journal.append(Cancelled("ord-2"))

        assertEquals(3, journal.size)
        assertEquals(
            """{"event":"placed","orderId":"ord-1","customer":"Zoe","totalCents":4250}""",
            journal.rawRecord(0),
        )
        assertEquals("""{"event":"cancelled","orderId":"ord-2"}""", journal.rawRecord(2))
        assertEquals(
            listOf(
                Placed("ord-1", customer = "Zoe", totalCents = 4_250),
                Shipped("ord-1", carrier = "PostNord"),
                Cancelled("ord-2"),
            ),
            journal.replay(),
        )
    }

    @Test
    fun `a single record is produced as text content labelled with the requested charset`() = runBlocking {
        val converter = KotlinxSerializationConverter(journalJson)
        val latin1: Charset = Charsets.ISO_8859_1
        val content: OutgoingContent = converter.serialize(
            ContentType.Application.Json.withCharset(latin1),
            latin1,
            typeInfo<Snapshot>(),
            Snapshot("ord-\u00e9", status = "livr\u00e9e", events = 2),
        ) ?: error("no content")

        assertTrue(content is TextContent, "expected text content but got ${content::class}")
        assertEquals(latin1, content.contentType?.charset())
        val bytes: ByteArray = drain(content)
        val expected = "{\"orderId\":\"ord-\u00e9\",\"status\":\"livr\u00e9e\",\"events\":2}"
        // ISO-8859-1 spends one byte per accented letter where UTF-8 would spend two.
        assertEquals(expected.length, bytes.size)
        assertEquals(expected, String(bytes, latin1))
        assertEquals(
            Snapshot("ord-\u00e9", status = "livr\u00e9e", events = 2),
            converter.deserialize(latin1, typeInfo<Snapshot>(), ByteReadChannel(bytes)),
        )
    }

    @Test
    fun `the whole journal is exported as a flow and replayed lazily as a sequence`() = runBlocking {
        val converter = KotlinxSerializationConverter(journalJson)
        val events: List<OrderEvent> = (1..25).map { n ->
            if (n % 5 == 0) Shipped("ord-$n", carrier = "DHL") else Placed("ord-$n", "customer-$n", n * 100L)
        }
        val export: OutgoingContent = converter.serialize(
            ContentType.Application.Json,
            Charsets.UTF_8,
            typeInfo<Flow<OrderEvent>>(),
            events.asFlow(),
        ) ?: error("flow export was not handled")
        val exported: ByteArray = drain(export)
        val text = String(exported, Charsets.UTF_8)
        assertTrue(text.startsWith("[{\"event\":\"placed\",\"orderId\":\"ord-1\""), text)
        assertTrue(text.endsWith("]"), text)

        val asList = converter.deserialize(Charsets.UTF_8, typeInfo<List<OrderEvent>>(), ByteReadChannel(exported))
        assertEquals(events, asList)

        @Suppress("UNCHECKED_CAST")
        val replay = converter.deserialize(
            Charsets.UTF_8,
            typeInfo<Sequence<OrderEvent>>(),
            ByteReadChannel(exported),
        ) as Sequence<OrderEvent>
        // The sequence reads the channel as it is iterated, so walk it off the calling thread.
        val shippedOrders: List<String> = withContext(Dispatchers.IO) {
            replay.filterIsInstance<Shipped>().map { it.orderId }.toList()
        }
        assertEquals(listOf("ord-5", "ord-10", "ord-15", "ord-20", "ord-25"), shippedOrders)
    }

    @Test
    fun `a missing snapshot is stored as JSON null and read back as null`() = runBlocking {
        val converter = KotlinxSerializationConverter(journalJson)
        val absent: Snapshot? = null
        val content: OutgoingContent = converter.serialize(
            ContentType.Application.Json,
            Charsets.UTF_8,
            typeInfo<Snapshot?>(),
            absent,
        ) ?: error("no content")
        val bytes: ByteArray = drain(content)
        assertEquals("null", String(bytes, Charsets.UTF_8))
        assertNull(converter.deserialize(Charsets.UTF_8, typeInfo<Snapshot?>(), ByteReadChannel(bytes)))
    }

    @Test
    fun `the kotlinx-io converter writes the same journal and replays records from the other converter`() =
        runBlocking {
            val streaming = Journal(ExperimentalJsonConverter(journalJson), Charsets.UTF_8)
            streaming.append(Placed("ord-9", customer = "Ana", totalCents = 990))
            streaming.append(Cancelled("ord-9", reason = "out of stock"))

            val classic = Journal(KotlinxSerializationConverter(journalJson), Charsets.UTF_8)
            classic.append(Placed("ord-9", customer = "Ana", totalCents = 990))
            classic.append(Cancelled("ord-9", reason = "out of stock"))

            assertEquals(classic.rawRecord(0), streaming.rawRecord(0))
            assertEquals(
                """{"event":"cancelled","orderId":"ord-9","reason":"out of stock"}""",
                streaming.rawRecord(1),
            )
            assertEquals(classic.replay(), streaming.replay())

            val snapshots: List<Snapshot> = listOf(Snapshot("ord-9", "cancelled", 2), Snapshot("ord-8", "open", 1))
            val content: OutgoingContent = ExperimentalJsonConverter(journalJson).serialize(
                ContentType.Application.Json,
                Charsets.UTF_8,
                typeInfo<List<Snapshot>>(),
                snapshots,
            ) ?: error("no content")
            val bytes: ByteArray = drain(content)
            assertEquals(
                snapshots,
                KotlinxSerializationConverter(journalJson)
                    .deserialize(Charsets.UTF_8, typeInfo<List<Snapshot>>(), ByteReadChannel(bytes)),
            )
        }

    @Test
    fun `corrupt or unknown records are rejected with a JSON conversion error`() = runBlocking {
        val converter = KotlinxSerializationConverter(journalJson)
        val truncated = assertThrows<JsonConvertException> {
            runBlocking {
                converter.deserialize(
                    Charsets.UTF_8,
                    typeInfo<OrderEvent>(),
                    ByteReadChannel("""{"event":"placed","orderId":"ord-1","custo""".toByteArray()),
                )
            }
        }
        assertTrue(truncated.message.orEmpty().startsWith("Illegal input"), truncated.message)

        val unknownType = assertThrows<JsonConvertException> {
            runBlocking {
                converter.deserialize(
                    Charsets.UTF_8,
                    typeInfo<OrderEvent>(),
                    ByteReadChannel("""{"event":"refunded","orderId":"ord-1"}""".toByteArray()),
                )
            }
        }
        assertTrue(unknownType.message.orEmpty().contains("refunded"), unknownType.message)
    }

    private companion object {
        /** Reads every byte an outgoing content would put on the wire. */
        suspend fun drain(content: OutgoingContent): ByteArray = when (content) {
            is OutgoingContent.ByteArrayContent -> content.bytes()
            is OutgoingContent.WriteChannelContent -> coroutineScope {
                val channel = ByteChannel()
                launch {
                    content.writeTo(channel)
                    channel.flushAndClose()
                }
                channel.readRemaining().readByteArray()
            }
            else -> error("unexpected content ${content::class}")
        }
    }
}
