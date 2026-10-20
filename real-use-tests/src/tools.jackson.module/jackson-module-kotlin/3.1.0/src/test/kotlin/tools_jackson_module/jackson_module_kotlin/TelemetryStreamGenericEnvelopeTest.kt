/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools_jackson_module.jackson_module_kotlin

import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tools.jackson.core.JsonToken
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonMapperBuilder
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import tools.jackson.module.kotlin.readValues

/**
 * Streaming reads of generic Kotlin envelope data classes through the reified parser, file and
 * byte-array extensions (`readValues` and `readValue` over a `JsonParser`, `readValue` from a
 * `File` and a `ByteArray`), as a telemetry collector ingests newline-delimited device batches.
 */
public class TelemetryStreamGenericEnvelopeTest {

    enum class MeasureUnit { CELSIUS, PERCENT, HECTOPASCAL }

    data class Reading(val sensor: String, val value: Double, val unit: MeasureUnit)

    data class Envelope<T>(
        val deviceId: String,
        val sequence: Long,
        val payload: T,
        val labels: Map<String, String> = emptyMap(),
    )

    data class Page<T>(
        val items: List<T>,
        val next: String? = null,
        val groups: Map<String, List<T>> = emptyMap(),
    )

    data class Alert(val code: String, val severity: Int, val reading: Reading?)

    private val mapper = jacksonObjectMapper()

    /** Reads values out of the middle of a document, so tokens after each value are expected. */
    private val pullMapper: ObjectMapper = jacksonMapperBuilder()
        .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .build()

    @Test
    fun streamsNewlineDelimitedEnvelopesWithGenericPayloads(): Unit {
        val ndjson = """
            {"deviceId":"greenhouse-1","sequence":1,"payload":[{"sensor":"t0","value":21.5,"unit":"CELSIUS"}]}
            {"deviceId":"greenhouse-1","sequence":2,"payload":[{"sensor":"h0","value":63.0,"unit":"PERCENT"},{"sensor":"p0","value":1013.2,"unit":"HECTOPASCAL"}],"labels":{"zone":"north"}}
            {"deviceId":"greenhouse-2","sequence":7,"payload":[]}
        """.trimIndent()

        val batches: List<Envelope<List<Reading>>> = mapper.createParser(ndjson).use { parser ->
            mapper.readValues<Envelope<List<Reading>>>(parser).asSequence().toList()
        }

        assertThat(batches).hasSize(3)
        assertThat(batches.map { it.sequence }).containsExactly(1L, 2L, 7L)
        assertThat(batches[0].payload).containsExactly(Reading("t0", 21.5, MeasureUnit.CELSIUS))
        assertThat(batches[1].payload.map { it.unit }).containsExactly(MeasureUnit.PERCENT, MeasureUnit.HECTOPASCAL)
        assertThat(batches[1].labels).containsEntry("zone", "north")
        assertThat(batches[2].payload).isEmpty()
        assertThat(batches[2].labels).isEmpty()

        val byDevice: Map<String, Int> = batches
            .groupBy { it.deviceId }
            .mapValues { (_, envelopes) -> envelopes.sumOf { it.payload.size } }
        assertThat(byDevice).containsExactlyInAnyOrderEntriesOf(mapOf("greenhouse-1" to 3, "greenhouse-2" to 0))
    }

    @Test
    fun pullsArrayElementsOneByOneFromALargeDocument(): Unit {
        val export = """
            {
              "exportedAt": "2026-10-16T08:00:00Z",
              "device": {"id": "greenhouse-3", "firmware": "4.2.0"},
              "readings": [
                {"sensor": "t0", "value": 19.0, "unit": "CELSIUS"},
                {"sensor": "t0", "value": 19.5, "unit": "CELSIUS"},
                {"sensor": "h0", "value": 58.25, "unit": "PERCENT"}
              ],
              "trailer": {"count": 3}
            }
        """.trimIndent()

        val collected = mutableListOf<Reading>()
        var firmware: String? = null
        pullMapper.createParser(export).use { parser ->
            assertThat(parser.nextToken()).isEqualTo(JsonToken.START_OBJECT)
            while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
                val field: String = parser.currentName()
                parser.nextToken()
                when (field) {
                    "device" -> firmware = pullMapper.readValue<Map<String, String>>(parser)["firmware"]
                    "readings" -> while (parser.nextToken() == JsonToken.START_OBJECT) {
                        collected += pullMapper.readValue<Reading>(parser)
                    }
                    else -> parser.skipChildren()
                }
            }
        }

        assertThat(firmware).isEqualTo("4.2.0")
        assertThat(collected).containsExactly(
            Reading("t0", 19.0, MeasureUnit.CELSIUS),
            Reading("t0", 19.5, MeasureUnit.CELSIUS),
            Reading("h0", 58.25, MeasureUnit.PERCENT),
        )
        assertThat(collected.filter { it.unit == MeasureUnit.CELSIUS }.map { it.value }.average()).isEqualTo(19.25)
    }

    @Test
    fun persistsAndReloadsNestedGenericPagesFromAFile(@TempDir spool: File): Unit {
        val warm = Reading("t1", 31.0, MeasureUnit.CELSIUS)
        val humid = Reading("h1", 91.5, MeasureUnit.PERCENT)
        val page = Envelope(
            deviceId = "greenhouse-4",
            sequence = 42L,
            payload = Page(
                items = listOf(Alert("TEMP_HIGH", 2, warm), Alert("LINK_DOWN", 3, null)),
                next = "cursor-43",
                groups = mapOf("sensor" to listOf(Alert("HUMIDITY_HIGH", 1, humid))),
            ),
            labels = mapOf("site" to "delft"),
        )
        val file = File(spool, "alerts-42.json")

        mapper.writeValue(file, page)
        val reloaded: Envelope<Page<Alert>> = mapper.readValue(file)

        assertThat(reloaded).isEqualTo(page)
        assertThat(reloaded.payload.items[0].reading?.unit).isEqualTo(MeasureUnit.CELSIUS)
        assertThat(reloaded.payload.items[1].reading).isNull()
        assertThat(reloaded.payload.groups.getValue("sensor").single().reading).isEqualTo(humid)
        assertThat(reloaded.payload.next).isEqualTo("cursor-43")
    }

    @Test
    fun decodesAByteArrayBodyIntoATypedLastPage(): Unit {
        val body: ByteArray = """
            {"deviceId":"greenhouse-5","sequence":9,
             "payload":{"items":[{"sensor":"p1","value":998.0,"unit":"HECTOPASCAL"}]}}
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val envelope: Envelope<Page<Reading>> = mapper.readValue(body)

        assertThat(envelope.deviceId).isEqualTo("greenhouse-5")
        assertThat(envelope.payload.next).isNull()
        assertThat(envelope.payload.groups).isEmpty()
        assertThat(envelope.payload.items.single()).isEqualTo(Reading("p1", 998.0, MeasureUnit.HECTOPASCAL))

        val echoed: Map<String, Any?> = mapper.readValue(mapper.writeValueAsBytes(envelope))
        assertThat(echoed.keys).containsExactlyInAnyOrder("deviceId", "sequence", "payload", "labels")
    }
}
