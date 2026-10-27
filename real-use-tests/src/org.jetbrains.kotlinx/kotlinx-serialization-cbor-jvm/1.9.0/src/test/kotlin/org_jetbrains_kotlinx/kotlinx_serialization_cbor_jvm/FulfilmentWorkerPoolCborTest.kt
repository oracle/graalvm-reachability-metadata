/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_cbor_jvm

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises one shared `Cbor` instance used concurrently by coroutine workers that exchange
 * encoded documents over channels.
 *
 * A fulfilment service receives pick tickets as CBOR frames, fans them out to a pool of
 * workers on `Dispatchers.Default`, and collects CBOR-encoded pick results; frames that do not
 * decode are routed to a dead-letter list instead of stopping the pool.
 */
@OptIn(ExperimentalSerializationApi::class)
public class FulfilmentWorkerPoolCborTest {

    @Serializable
    public data class PickLine(val sku: String, val quantity: Int, val bin: String)

    @Serializable
    public data class PickTicket(val ticketId: Long, val warehouse: String, val lines: List<PickLine>)

    @Serializable
    public data class PickResult(
        val ticketId: Long,
        val totalUnits: Int,
        val binsVisited: List<String>,
        val picker: String,
    )

    /** A frame the pool could not process, kept with the reason for operators. */
    public data class DeadLetter(val frame: ByteArray, val reason: String)

    /** Everything one pool run produced, keyed for order-independent assertions. */
    public class PoolOutcome(val results: Map<Long, PickResult>, val deadLetters: List<DeadLetter>)

    /** Decodes tickets, plans the pick walk and encodes the result, all through one codec. */
    public class FulfilmentPool(private val codec: Cbor, private val workerCount: Int) {

        public fun process(frames: List<ByteArray>): PoolOutcome = runBlocking {
            withContext(Dispatchers.Default) {
                val inbox: Channel<ByteArray> = Channel(capacity = 8)
                val outbox: Channel<ByteArray> = Channel(capacity = Channel.UNLIMITED)
                val deadLetters: Channel<DeadLetter> = Channel(capacity = Channel.UNLIMITED)

                coroutineScope {
                    launch {
                        frames.forEach { frame: ByteArray -> inbox.send(frame) }
                        inbox.close()
                    }
                    val workers: List<Job> = (1..workerCount).map { index: Int ->
                        launch { work("picker-$index", inbox, outbox, deadLetters) }
                    }
                    workers.forEach { worker: Job -> worker.join() }
                    outbox.close()
                    deadLetters.close()
                }

                val results: MutableMap<Long, PickResult> = mutableMapOf()
                for (encoded: ByteArray in outbox) {
                    val result: PickResult = codec.decodeFromByteArray<PickResult>(encoded)
                    check(results.put(result.ticketId, result) == null) { "duplicate ${result.ticketId}" }
                }
                val failed: MutableList<DeadLetter> = mutableListOf()
                for (letter: DeadLetter in deadLetters) {
                    failed += letter
                }
                PoolOutcome(results, failed)
            }
        }

        private suspend fun work(
            picker: String,
            inbox: Channel<ByteArray>,
            outbox: Channel<ByteArray>,
            deadLetters: Channel<DeadLetter>,
        ) {
            for (frame: ByteArray in inbox) {
                val ticket: PickTicket = try {
                    codec.decodeFromByteArray<PickTicket>(frame)
                } catch (failure: SerializationException) {
                    deadLetters.send(DeadLetter(frame, failure.message ?: failure.toString()))
                    continue
                }
                outbox.send(codec.encodeToByteArray(plan(ticket, picker)))
            }
        }

        private fun plan(ticket: PickTicket, picker: String): PickResult = PickResult(
            ticketId = ticket.ticketId,
            totalUnits = ticket.lines.sumOf { line: PickLine -> line.quantity },
            binsVisited = ticket.lines.map { line: PickLine -> line.bin }.distinct().sorted(),
            picker = picker,
        )
    }

    private val codec: Cbor = Cbor {
        ignoreUnknownKeys = true
        useDefiniteLengthEncoding = true
    }

    private fun ticket(id: Long): PickTicket = PickTicket(
        ticketId = id,
        warehouse = if (id % 2 == 0L) "north" else "south",
        lines = (0 until (id % 5 + 1).toInt()).map { line: Int ->
            PickLine(sku = "SKU-$id-$line", quantity = line + 1, bin = "B${(id + line) % 7}")
        },
    )

    @Test
    fun workerPoolProcessesEveryTicketThroughTheSharedCodec(): Unit {
        val tickets: List<PickTicket> = (1L..400L).map { id: Long -> ticket(id) }
        val frames: List<ByteArray> = runBlocking {
            withContext(Dispatchers.Default) {
                tickets.map { ticket: PickTicket -> async { codec.encodeToByteArray(ticket) } }
                    .awaitAll()
            }
        }
        frames.zip(tickets).forEach { (frame: ByteArray, ticket: PickTicket) ->
            assertArrayEquals(codec.encodeToByteArray(ticket), frame)
        }

        val outcome: PoolOutcome = FulfilmentPool(codec, workerCount = 6).process(frames)

        assertTrue(outcome.deadLetters.isEmpty())
        assertEquals(tickets.map { ticket: PickTicket -> ticket.ticketId }.toSet(), outcome.results.keys)
        val sample: PickResult = outcome.results.getValue(14L)
        assertEquals(1 + 2 + 3 + 4 + 5, sample.totalUnits)
        assertEquals(listOf("B0", "B1", "B2", "B3", "B4"), sample.binsVisited)
        assertTrue(sample.picker.startsWith("picker-"))
        tickets.forEach { ticket: PickTicket ->
            val result: PickResult = outcome.results.getValue(ticket.ticketId)
            assertEquals(ticket.lines.sumOf { line: PickLine -> line.quantity }, result.totalUnits)
        }
    }

    @Test
    fun malformedFramesAreDeadLetteredWhileTheRestOfTheBatchCompletes(): Unit {
        val good: List<ByteArray> = (1L..60L).map { id: Long -> codec.encodeToByteArray(ticket(id)) }
        val truncated: ByteArray = good[10].copyOfRange(0, good[10].size - 3)
        val wrongShape: ByteArray = codec.encodeToByteArray(PickLine("SKU-X", 1, "B9"))
        val frames: List<ByteArray> = good.subList(0, 30) + truncated + good.subList(30, 60) + wrongShape

        val outcome: PoolOutcome = FulfilmentPool(codec, workerCount = 4).process(frames)

        assertEquals((1L..60L).toSet(), outcome.results.keys)
        assertEquals(2, outcome.deadLetters.size)
        val rejected: Set<Int> = outcome.deadLetters.map { letter: DeadLetter -> letter.frame.size }.toSet()
        assertEquals(setOf(truncated.size, wrongShape.size), rejected)
        outcome.deadLetters.forEach { letter: DeadLetter -> assertTrue(letter.reason.isNotBlank()) }
    }

    @Test
    fun concurrentRoundTripsOfDifferentDocumentTypesStayIsolated(): Unit {
        val decoded: List<Pair<PickTicket, PickResult>> = runBlocking {
            withContext(Dispatchers.Default) {
                val jobs: List<Deferred<Pair<PickTicket, PickResult>>> = (1L..200L).map { id: Long ->
                    async {
                        val ticketBytes: ByteArray = codec.encodeToByteArray(ticket(id))
                        val result: PickResult = PickResult(id, id.toInt(), listOf("B${id % 7}"), "solo")
                        val resultBytes: ByteArray = codec.encodeToByteArray(result)
                        codec.decodeFromByteArray<PickTicket>(ticketBytes) to
                            codec.decodeFromByteArray<PickResult>(resultBytes)
                    }
                }
                jobs.awaitAll()
            }
        }

        decoded.forEachIndexed { index: Int, (ticket: PickTicket, result: PickResult) ->
            val id: Long = index + 1L
            assertEquals(ticket(id), ticket)
            assertEquals(PickResult(id, id.toInt(), listOf("B${id % 7}"), "solo"), result)
        }
    }
}
