/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_content_negotiation_jvm

import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
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
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.readText
import io.ktor.utils.io.readRemaining
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises custom content-converter registration for a warehouse CSV manifest import and export.
 */
@Timeout(60)
class CustomCsvManifestConverterTest {
    @Test
    fun importsScannerCsvAndServesTheManifestAsJsonOrCsv(): Unit = testApplication {
        val csvType: ContentType = ContentType("text", "csv")
        val stored: AtomicReference<Manifest> = AtomicReference(Manifest(emptyList()))
        application {
            install(ContentNegotiation) {
                register(csvType, ManifestCsvConverter())
                json()
            }
            routing {
                post("/manifest") {
                    val manifest: Manifest = call.receive<Manifest>()
                    stored.set(manifest)
                    call.respond(HttpStatusCode.Created, manifest)
                }
                get("/manifest") {
                    call.respond(stored.get())
                }
            }
        }

        val warehouseClient = createClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 20_000
                socketTimeoutMillis = 20_000
            }
        }
        val scannerCsv: String = "sku,quantity\r\nBOX-10,7\r\nTAPE-2,12\r\n"
        val expected: Manifest = Manifest(listOf(StockLine("BOX-10", 7), StockLine("TAPE-2", 12)))

        // Scanner uploads use the custom format; the management UI receives ordinary JSON.
        val importResponse = warehouseClient.post("/manifest") {
            contentType(csvType.withCharset(Charsets.UTF_8))
            accept(ContentType.Application.Json)
            setBody(scannerCsv)
        }
        assertEquals(HttpStatusCode.Created, importResponse.status)
        assertEquals(ContentType.Application.Json, importResponse.contentType()?.withoutParameters())
        assertEquals(expected, Json.decodeFromString<Manifest>(importResponse.bodyAsText()))

        val exportResponse = warehouseClient.get("/manifest") {
            accept(csvType)
        }
        assertEquals(HttpStatusCode.OK, exportResponse.status)
        assertEquals(csvType, exportResponse.contentType()?.withoutParameters())
        assertEquals(scannerCsv, exportResponse.bodyAsText())

        val dashboardResponse = warehouseClient.get("/manifest") {
            accept(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.OK, dashboardResponse.status)
        assertEquals(ContentType.Application.Json, dashboardResponse.contentType()?.withoutParameters())
        assertEquals(expected, Json.decodeFromString<Manifest>(dashboardResponse.bodyAsText()))
    }

    @Serializable
    data class Manifest(val lines: List<StockLine>)

    @Serializable
    data class StockLine(val sku: String, val quantity: Int)

    // The scanner protocol permits only unquoted, alphanumeric SKU identifiers with hyphens.
    class ManifestCsvConverter : ContentConverter {
        override suspend fun serialize(
            contentType: ContentType,
            charset: Charset,
            typeInfo: TypeInfo,
            value: Any?
        ): OutgoingContent? {
            if (value !is Manifest) return null
            val csv: String = buildString {
                append("sku,quantity\r\n")
                for (line: StockLine in value.lines) {
                    require(line.sku.matches(Regex("[A-Z0-9-]+")))
                    require(line.quantity >= 0)
                    append("${line.sku},${line.quantity}\r\n")
                }
            }
            return TextContent(csv, contentType.withCharset(charset))
        }

        override suspend fun deserialize(
            charset: Charset,
            typeInfo: TypeInfo,
            content: ByteReadChannel
        ): Any? {
            if (typeInfo.type != Manifest::class) return null
            val rows: List<String> = content.readRemaining().readText(charset).trimEnd('\r', '\n').lines()
            require(rows.first() == "sku,quantity")
            val lines: List<StockLine> = rows.drop(1).map { row ->
                val fields: List<String> = row.split(',')
                require(fields.size == 2)
                require(fields[0].matches(Regex("[A-Z0-9-]+")))
                val quantity: Int = fields[1].toInt()
                require(quantity >= 0)
                StockLine(fields[0], quantity)
            }
            return Manifest(lines)
        }
    }
}
