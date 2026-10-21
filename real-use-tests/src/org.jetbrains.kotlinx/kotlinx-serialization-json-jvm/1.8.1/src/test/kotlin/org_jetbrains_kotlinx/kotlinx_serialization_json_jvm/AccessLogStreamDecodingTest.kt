/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_json_jvm

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.decodeToSequence
import kotlinx.serialization.json.encodeToStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Exercises the JVM stream API of `Json`: `encodeToStream`, `decodeFromStream`, and lazy
 * `decodeToSequence` over newline-delimited and array-wrapped input in every `DecodeSequenceMode`.
 *
 * A log shipper appends access records to a newline-delimited JSON file, an aggregator reads that
 * file and an array-wrapped export lazily to build per-route statistics, and the resulting report
 * is written to and read back from a file stream.
 */
@OptIn(ExperimentalSerializationApi::class)
public class AccessLogStreamDecodingTest {

    @Serializable
    public data class AccessRecord(
        val route: String,
        val status: Int,
        @SerialName("duration_ms") val durationMs: Long,
        val client: String? = null,
    )

    @Serializable
    public data class RouteStats(
        val requests: Int,
        val errors: Int,
        @SerialName("max_duration_ms") val maxDurationMs: Long,
    )

    @Serializable
    public data class TrafficReport(
        val source: String,
        val routes: Map<String, RouteStats>,
        val clients: Set<String>,
    )

    /** Aggregates access records consumed one by one from a lazy sequence. */
    public class Aggregator {
        private val stats: MutableMap<String, RouteStats> = sortedMapOf()
        private val clients: MutableSet<String> = sortedSetOf()
        public var consumed: Int = 0
            private set

        public fun accept(record: AccessRecord) {
            consumed++
            record.client?.let { clients += it }
            val previous: RouteStats = stats[record.route] ?: RouteStats(0, 0, 0)
            stats[record.route] = RouteStats(
                requests = previous.requests + 1,
                errors = previous.errors + if (record.status >= 500) 1 else 0,
                maxDurationMs = maxOf(previous.maxDurationMs, record.durationMs),
            )
        }

        public fun report(source: String): TrafficReport = TrafficReport(source, stats.toMap(), clients.toSet())
    }

    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val shipped: List<AccessRecord> = listOf(
        AccessRecord("/orders", 200, 35, "web"),
        AccessRecord("/orders", 503, 1200, "mobile"),
        AccessRecord("/health", 200, 2),
        AccessRecord("/orders/42", 404, 18, "web"),
        AccessRecord("/orders", 200, 80, "batch"),
    )

    @Test
    fun shipperAppendsNdjsonAndAggregatorReadsItLazily(@TempDir dir: Path) {
        val logFile: Path = dir.resolve("access.ndjson")
        Files.newOutputStream(logFile).use { out ->
            shipped.forEach { record ->
                json.encodeToStream(record, out)
                out.write('\n'.code)
            }
        }
        val lines: List<String> = Files.readAllLines(logFile)
        assertEquals(5, lines.size)
        assertEquals("""{"route":"/health","status":200,"duration_ms":2}""", lines[2])

        val aggregator: Aggregator = Aggregator()
        Files.newInputStream(logFile).use { input ->
            val records: Sequence<AccessRecord> =
                json.decodeToSequence<AccessRecord>(input, DecodeSequenceMode.WHITESPACE_SEPARATED)
            records.forEach(aggregator::accept)
        }

        assertEquals(5, aggregator.consumed)
        val report: TrafficReport = aggregator.report("access.ndjson")
        assertEquals(RouteStats(requests = 3, errors = 1, maxDurationMs = 1200), report.routes["/orders"])
        assertEquals(RouteStats(requests = 1, errors = 0, maxDurationMs = 2), report.routes["/health"])
        assertEquals(RouteStats(requests = 1, errors = 0, maxDurationMs = 18), report.routes["/orders/42"])
        assertEquals(setOf("batch", "mobile", "web"), report.clients)
    }

    @Test
    fun aggregatorStopsReadingEarlyFromArrayWrappedExport() {
        val export: String = """
            [
              {"route": "/orders", "status": 200, "duration_ms": 12, "region": "eu"},
              {"route": "/orders", "status": 500, "duration_ms": 900, "region": "us"},
              {"route": "/cart", "status": 201, "duration_ms": 40},
              this is not json and must never be parsed
            ]
        """.trimIndent()

        ByteArrayInputStream(export.toByteArray()).use { input ->
            val firstThree: List<AccessRecord> =
                json.decodeToSequence<AccessRecord>(input, DecodeSequenceMode.ARRAY_WRAPPED).take(3).toList()
            assertEquals(listOf("/orders", "/orders", "/cart"), firstThree.map(AccessRecord::route))
            assertEquals(listOf(200, 500, 201), firstThree.map(AccessRecord::status))
            assertEquals(null, firstThree[2].client)
        }

        ByteArrayInputStream(export.toByteArray()).use { input ->
            val all: Sequence<AccessRecord> = json.decodeToSequence<AccessRecord>(input, DecodeSequenceMode.ARRAY_WRAPPED)
            assertThrows(SerializationException::class.java) { all.toList() }
        }
    }

    @Test
    fun autoDetectAcceptsBothShapesFromUpstreamShippers() {
        val ndjson: String = shipped.joinToString("\n") { json.encodeToString(AccessRecord.serializer(), it) }
        val array: String = shipped.joinToString(",", prefix = "[", postfix = "]") {
            json.encodeToString(AccessRecord.serializer(), it)
        }

        val fromLines: List<AccessRecord> = ByteArrayInputStream(ndjson.toByteArray()).use { input ->
            json.decodeToSequence<AccessRecord>(input, DecodeSequenceMode.AUTO_DETECT).toList()
        }
        val fromArray: List<AccessRecord> = ByteArrayInputStream(array.toByteArray()).use { input ->
            json.decodeToSequence<AccessRecord>(input).toList()
        }

        assertEquals(shipped, fromLines)
        assertEquals(shipped, fromArray)
    }

    @Test
    fun reportRoundTripsThroughFileStreams(@TempDir dir: Path) {
        val aggregator: Aggregator = Aggregator()
        shipped.forEach(aggregator::accept)
        val report: TrafficReport = aggregator.report("nightly")

        val reportFile: Path = dir.resolve("report.json")
        Files.newOutputStream(reportFile).use { out -> json.encodeToStream(report, out) }
        val written: String = Files.readString(reportFile)
        assertTrue(written.startsWith("""{"source":"nightly","routes":{"/health":"""), written)

        val restored: TrafficReport = Files.newInputStream(reportFile).use { input ->
            json.decodeFromStream<TrafficReport>(input)
        }
        assertEquals(report, restored)

        val buffer: ByteArrayOutputStream = ByteArrayOutputStream()
        json.encodeToStream(TrafficReport.serializer(), restored, buffer)
        assertEquals(written, buffer.toString(Charsets.UTF_8))
    }
}
