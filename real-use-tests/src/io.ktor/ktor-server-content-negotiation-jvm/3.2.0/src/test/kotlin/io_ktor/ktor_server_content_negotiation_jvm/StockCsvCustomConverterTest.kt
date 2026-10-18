/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_content_negotiation_jvm

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.charset
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.http.withCharset
import io.ktor.serialization.ContentConverter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.toByteArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Application-defined content converters registered with `register` and chained with built-in ones.
 *
 * A warehouse service exports stock reports as CSV for spreadsheet users and takes stock
 * adjustments as CSV uploads, with its own `ContentConverter` registered ahead of kotlinx JSON.
 * The CSV converter handles only the types it knows and returns `null` for anything else. The
 * test checks that the plugin hands report bodies to the custom converter in the charset the
 * caller asks for with `Accept-Charset`, that a type the CSV converter declines falls through to
 * the next acceptable converter, that CSV uploads decode through the custom converter, and that
 * an upload the converter declines is rejected instead of reaching the route.
 */
class StockCsvCustomConverterTest {

    data class StockLevel(val sku: String, val warehouse: String, val onHand: Int)

    data class StockReport(val levels: List<StockLevel>)

    data class Adjustment(val sku: String, val delta: Int)

    data class AdjustmentBatch(val adjustments: List<Adjustment>)

    @Serializable
    data class StockSummary(val skus: Int, val units: Int)

    @Serializable
    data class Reorder(val sku: String, val quantity: Int)

    /** Writes [StockReport] and reads [AdjustmentBatch] as CSV; declines every other type. */
    class StockCsvConverter : ContentConverter {

        override suspend fun serialize(
            contentType: ContentType,
            charset: Charset,
            typeInfo: TypeInfo,
            value: Any?,
        ): OutgoingContent? {
            if (value !is StockReport) {
                return null
            }
            val rows: List<String> = value.levels.map { "${it.sku},${it.warehouse},${it.onHand}" }
            val csv: String = (listOf("sku,warehouse,on_hand") + rows).joinToString("\r\n", postfix = "\r\n")
            return TextContent(csv, contentType.withCharset(charset))
        }

        override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any? {
            if (typeInfo.type != AdjustmentBatch::class) {
                return null
            }
            val text: String = String(content.toByteArray(), charset)
            val adjustments: List<Adjustment> = text.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .drop(1)
                .map { line ->
                    val cells: List<String> = line.split(',')
                    Adjustment(cells[0], cells[1].toInt())
                }
                .toList()
            return AdjustmentBatch(adjustments)
        }
    }

    private val applied: ConcurrentLinkedQueue<Adjustment> = ConcurrentLinkedQueue()
    private val reorders: ConcurrentLinkedQueue<Reorder> = ConcurrentLinkedQueue()

    private fun runService(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) {
                register(ContentType.Text.CSV, StockCsvConverter())
                json()
            }
            routing {
                get("/stock") {
                    call.respond(REPORT)
                }
                get("/stock/summary") {
                    call.respond(StockSummary(REPORT.levels.size, REPORT.levels.sumOf { it.onHand }))
                }
                post("/stock/adjustments") {
                    val batch: AdjustmentBatch = call.receive()
                    applied.addAll(batch.adjustments)
                    call.respondText("applied=${batch.adjustments.size}", status = HttpStatusCode.Accepted)
                }
                post("/stock/reorders") {
                    val reorder: Reorder = call.receive()
                    reorders.add(reorder)
                    call.respondText("reordered=${reorder.sku}", status = HttpStatusCode.Accepted)
                }
            }
        }
        block()
    }

    @Test
    fun reportIsExportedThroughTheCustomConverter() = runService {
        val response: HttpResponse = client.get("/stock") { header(HttpHeaders.Accept, "text/csv") }
        assertEquals(HttpStatusCode.OK, response.status)
        assertFormat(ContentType.Text.CSV, response)
        assertEquals(Charsets.UTF_8, response.contentType()!!.charset())
        assertEquals(EXPECTED_CSV, response.bodyAsText())
    }

    @Test
    fun acceptCharsetSelectsTheEncodingGivenToTheConverter() = runService {
        val response: HttpResponse = client.get("/stock") {
            header(HttpHeaders.Accept, "text/csv")
            header(HttpHeaders.AcceptCharset, "ISO-8859-1")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertFormat(ContentType.Text.CSV, response)
        assertEquals(Charsets.ISO_8859_1, response.contentType()!!.charset())
        val bytes: ByteArray = response.bodyAsBytes()
        assertEquals(EXPECTED_CSV, String(bytes, Charsets.ISO_8859_1))
        assertEquals(EXPECTED_CSV.length, bytes.size)
    }

    @Test
    fun declinedTypeFallsThroughToTheNextAcceptableConverter() = runService {
        val response: HttpResponse = client.get("/stock/summary") {
            header(HttpHeaders.Accept, "text/csv, application/json;q=0.5")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertFormat(ContentType.Application.Json, response)
        assertEquals(StockSummary(3, 57), Json.decodeFromString<StockSummary>(response.bodyAsText()))

        val report: HttpResponse = client.get("/stock") {
            header(HttpHeaders.Accept, "text/csv, application/json;q=0.5")
        }
        assertFormat(ContentType.Text.CSV, report)
        assertEquals(EXPECTED_CSV, report.bodyAsText())
    }

    @Test
    fun csvUploadsDecodeThroughTheCustomConverter() = runService {
        val upload: String = "sku,delta\r\nvalve-22,-4\r\npump-7,12\r\n"
        val response: HttpResponse = client.post("/stock/adjustments") {
            setBody(TextContent(upload, ContentType.Text.CSV))
        }
        assertEquals(HttpStatusCode.Accepted, response.status)
        assertEquals("applied=2", response.bodyAsText())
        assertEquals(listOf(Adjustment("valve-22", -4), Adjustment("pump-7", 12)), applied.toList())
    }

    @Test
    fun uploadTheConverterDeclinesNeverReachesTheRoute() = runService {
        val declined: HttpResponse = client.post("/stock/reorders") {
            setBody(TextContent("sku,quantity\r\nvalve-22,40\r\n", ContentType.Text.CSV))
        }
        assertEquals(HttpStatusCode.UnsupportedMediaType, declined.status)
        assertEquals(emptyList<Reorder>(), reorders.toList())

        val json: HttpResponse = client.post("/stock/reorders") {
            setBody(TextContent("""{"sku":"valve-22","quantity":40}""", ContentType.Application.Json))
        }
        assertEquals(HttpStatusCode.Accepted, json.status)
        assertEquals("reordered=valve-22", json.bodyAsText())
        assertEquals(listOf(Reorder("valve-22", 40)), reorders.toList())
    }

    private fun assertFormat(expected: ContentType, response: HttpResponse) {
        val actual: ContentType? = response.contentType()
        assertTrue(actual != null && actual.match(expected), "expected $expected, got $actual")
    }

    private companion object {
        val REPORT: StockReport = StockReport(
            listOf(
                StockLevel("valve-22", "M\u00fcnchen", 18),
                StockLevel("pump-7", "Gen\u00e8ve", 27),
                StockLevel("seal-3", "Oslo", 12),
            ),
        )
        val EXPECTED_CSV: String =
            "sku,warehouse,on_hand\r\nvalve-22,M\u00fcnchen,18\r\npump-7,Gen\u00e8ve,27\r\nseal-3,Oslo,12\r\n"
    }
}
