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
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.cbor.cbor
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationPlugin
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.ResponseBodyReadyForSend
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.toByteArray
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Application plugins whose body hooks run around the plugin's conversions: before decoding and after encoding.
 *
 * A product-catalog service takes supplier imports that mobile clients gzip before
 * uploading, and serves the catalog as JSON or CBOR with representation tags for cheap
 * revalidation. Two application plugins sit around `ContentNegotiation`: one, installed
 * ahead of it, inflates gzip-encoded request bodies in its own `onCallReceive` body
 * transform, so the converter decodes plain bytes; the other tags each encoded `GET`
 * response with a SHA-256 `ETag` from `ResponseBodyReadyForSend` and answers a matching
 * `If-None-Match` with `304 Not Modified`. The test checks that compressed and plain imports
 * decode into the same batch, that raw-text reads see inflated bytes even though the
 * converters skip them, that tags fingerprint the bytes each converter wrote, so JSON and
 * CBOR carry different tags, and that a tag stops matching once the catalog changes.
 */
@OptIn(ExperimentalSerializationApi::class)
class CatalogBodyHooksOrderingTest {

    @Serializable
    data class Product(val sku: String, val name: String, val priceCents: Long)

    @Serializable
    data class ImportBatch(val supplier: String, val products: List<Product>)

    @Serializable
    data class ImportSummary(val supplier: String, val imported: Int, val catalogSize: Int)

    /** The empty body a matching `If-None-Match` is answered with. */
    private object NotModifiedContent : OutgoingContent.NoContent() {
        override val status: HttpStatusCode = HttpStatusCode.NotModified
    }

    private val catalog: ConcurrentHashMap<String, Product> = ConcurrentHashMap()
    private val supplierNotes: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()

    private fun runCatalog(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(GzipUploads)
            install(ContentNegotiation) {
                json()
                cbor()
            }
            install(RepresentationTags)
            routing {
                post("/imports") {
                    val batch: ImportBatch = call.receive()
                    batch.products.forEach { catalog[it.sku] = it }
                    call.respond(HttpStatusCode.Created, ImportSummary(batch.supplier, batch.products.size, catalog.size))
                }
                post("/imports/notes") {
                    val note: String = call.receiveText()
                    supplierNotes.add(note)
                    call.respondText("noted ${note.length} chars", status = HttpStatusCode.Accepted)
                }
                get("/catalog") {
                    val products: List<Product> = catalog.values.sortedBy { it.sku }
                    call.respond(products)
                }
                get("/catalog/{sku}") {
                    val product: Product? = catalog[call.parameters["sku"]!!]
                    if (product == null) {
                        call.respond(HttpStatusCode.NotFound, "unknown sku")
                    } else {
                        call.respond(product)
                    }
                }
            }
        }
        block()
    }

    @Test
    fun gzippedAndPlainImportsDecodeIntoTheSameBatch() = runCatalog {
        val plain: HttpResponse = client.post("/imports") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(ImportBatch.serializer(), SPRING_BATCH))
        }
        assertEquals(HttpStatusCode.Created, plain.status)
        assertEquals(ImportSummary("northwind", 2, 2), Json.decodeFromString<ImportSummary>(plain.bodyAsText()))

        val gzipped: HttpResponse = client.post("/imports") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.ContentEncoding, "gzip")
            setBody(gzip(Json.encodeToString(ImportBatch.serializer(), SUMMER_BATCH).toByteArray()))
        }
        assertEquals(HttpStatusCode.Created, gzipped.status)
        assertEquals(ImportSummary("contoso", 2, 3), Json.decodeFromString<ImportSummary>(gzipped.bodyAsText()))
        assertNull(gzipped.headers[HttpHeaders.ETag])

        val gzippedCbor: HttpResponse = client.post("/imports") {
            contentType(ContentType.Application.Cbor)
            header(HttpHeaders.ContentEncoding, "gzip")
            header(HttpHeaders.Accept, ContentType.Application.Cbor.toString())
            setBody(gzip(Cbor.encodeToByteArray(ImportBatch.serializer(), SPRING_BATCH)))
        }
        assertEquals(HttpStatusCode.Created, gzippedCbor.status)
        assertEquals(ContentType.Application.Cbor, gzippedCbor.contentType()!!.withoutParameters())
        assertEquals(ImportSummary("northwind", 2, 3), Cbor.decodeFromByteArray<ImportSummary>(gzippedCbor.bodyAsBytes()))

        val expected: Map<String, Product> = (SPRING_BATCH.products + SUMMER_BATCH.products.drop(1) + SPRING_BATCH.products)
            .associateBy { it.sku }
        assertEquals(expected, catalog.toMap())
    }

    @Test
    fun rawTextReadsSeeInflatedBytesThatTheConvertersSkip() = runCatalog {
        val note: String = "Prices valid until 30 June; MOQ 12 units per SKU."
        val response: HttpResponse = client.post("/imports/notes") {
            contentType(ContentType.Text.Plain)
            header(HttpHeaders.ContentEncoding, "gzip")
            setBody(gzip(note.toByteArray()))
        }
        assertEquals(HttpStatusCode.Accepted, response.status)
        assertEquals(ContentType.Text.Plain, response.contentType()!!.withoutParameters())
        assertEquals("noted ${note.length} chars", response.bodyAsText())
        assertEquals(listOf(note), supplierNotes.toList())
    }

    @Test
    fun tagsFingerprintTheBytesEachConverterWrote() = runCatalog {
        importPlain(SPRING_BATCH)

        val json: HttpResponse = fetch("/catalog/brk-100", ContentType.Application.Json, null)
        assertEquals(HttpStatusCode.OK, json.status)
        assertEquals(ContentType.Application.Json, json.contentType()!!.withoutParameters())
        val jsonBytes: ByteArray = json.bodyAsBytes()
        assertEquals(SPRING_BATCH.products[0], Json.decodeFromString<Product>(jsonBytes.decodeToString()))
        val jsonTag: String = json.headers[HttpHeaders.ETag]!!
        assertEquals(tagOf(jsonBytes), jsonTag)
        assertEquals(HttpHeaders.Accept, json.headers[HttpHeaders.Vary])

        val cbor: HttpResponse = fetch("/catalog/brk-100", ContentType.Application.Cbor, null)
        assertEquals(HttpStatusCode.OK, cbor.status)
        assertEquals(ContentType.Application.Cbor, cbor.contentType()!!.withoutParameters())
        val cborBytes: ByteArray = cbor.bodyAsBytes()
        assertEquals(SPRING_BATCH.products[0], Cbor.decodeFromByteArray<Product>(cborBytes))
        val cborTag: String = cbor.headers[HttpHeaders.ETag]!!
        assertEquals(tagOf(cborBytes), cborTag)
        assertNotEquals(jsonTag, cborTag)

        val revalidated: HttpResponse = fetch("/catalog/brk-100", ContentType.Application.Json, jsonTag)
        assertEquals(HttpStatusCode.NotModified, revalidated.status)
        assertEquals(jsonTag, revalidated.headers[HttpHeaders.ETag])
        assertArrayEquals(ByteArray(0), revalidated.bodyAsBytes())

        val otherRepresentation: HttpResponse = fetch("/catalog/brk-100", ContentType.Application.Cbor, jsonTag)
        assertEquals(HttpStatusCode.OK, otherRepresentation.status)
        assertArrayEquals(cborBytes, otherRepresentation.bodyAsBytes())

        val missing: HttpResponse = fetch("/catalog/nope-1", ContentType.Application.Json, null)
        assertEquals(HttpStatusCode.NotFound, missing.status)
        assertNull(missing.headers[HttpHeaders.ETag])
    }

    @Test
    fun listingTagStopsMatchingOnceTheCatalogChanges() = runCatalog {
        importPlain(SPRING_BATCH)

        val before: HttpResponse = fetch("/catalog", ContentType.Application.Json, null)
        assertEquals(HttpStatusCode.OK, before.status)
        val beforeTag: String = before.headers[HttpHeaders.ETag]!!
        assertEquals(SPRING_BATCH.products.sortedBy { it.sku }, Json.decodeFromString<List<Product>>(before.bodyAsText()))
        assertEquals(HttpStatusCode.NotModified, fetch("/catalog", ContentType.Application.Json, beforeTag).status)

        importPlain(SUMMER_BATCH)

        val after: HttpResponse = fetch("/catalog", ContentType.Application.Json, beforeTag)
        assertEquals(HttpStatusCode.OK, after.status)
        val afterBytes: ByteArray = after.bodyAsBytes()
        assertEquals(tagOf(afterBytes), after.headers[HttpHeaders.ETag])
        assertNotEquals(beforeTag, after.headers[HttpHeaders.ETag])
        val listed: List<Product> = Json.decodeFromString(afterBytes.decodeToString())
        assertEquals(listOf("brk-100", "cbl-310", "hng-205"), listed.map { it.sku })
        assertEquals(1_349L, listed.first { it.sku == "brk-100" }.priceCents)
    }

    private suspend fun ApplicationTestBuilder.importPlain(batch: ImportBatch) {
        val response: HttpResponse = client.post("/imports") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(ImportBatch.serializer(), batch))
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    private suspend fun ApplicationTestBuilder.fetch(path: String, accept: ContentType, ifNoneMatch: String?): HttpResponse =
        client.get(path) {
            header(HttpHeaders.Accept, accept.toString())
            if (ifNoneMatch != null) {
                header(HttpHeaders.IfNoneMatch, ifNoneMatch)
            }
        }

    companion object {
        val SPRING_BATCH: ImportBatch = ImportBatch(
            "northwind",
            listOf(Product("brk-100", "Shelf bracket", 1_199), Product("hng-205", "Cabinet hinge", 349)),
        )
        val SUMMER_BATCH: ImportBatch = ImportBatch(
            "contoso",
            listOf(Product("brk-100", "Shelf bracket", 1_349), Product("cbl-310", "Cable tie pack", 499)),
        )

        /** Inflates gzip-encoded request bodies before any later receive transform sees them. */
        val GzipUploads: ApplicationPlugin<Unit> = createApplicationPlugin("GzipUploads") {
            onCallReceive { call ->
                if (call.request.headers[HttpHeaders.ContentEncoding] == "gzip") {
                    transformBody { body: ByteReadChannel ->
                        val compressed: ByteArray = body.toByteArray()
                        ByteReadChannel(GZIPInputStream(ByteArrayInputStream(compressed)).use { it.readBytes() })
                    }
                }
            }
        }

        /** Tags encoded `GET` responses with a digest of their bytes and honours `If-None-Match`. */
        val RepresentationTags: ApplicationPlugin<Unit> = createApplicationPlugin("RepresentationTags") {
            on(ResponseBodyReadyForSend) { call, content ->
                val status: HttpStatusCode = content.status ?: call.response.status() ?: HttpStatusCode.OK
                if (call.request.httpMethod != HttpMethod.Get || status != HttpStatusCode.OK) {
                    return@on
                }
                if (content !is OutgoingContent.ByteArrayContent) {
                    return@on
                }
                val tag: String = tagOf(content.bytes())
                call.response.headers.append(HttpHeaders.ETag, tag)
                call.response.headers.append(HttpHeaders.Vary, HttpHeaders.Accept)
                if (call.request.headers[HttpHeaders.IfNoneMatch] == tag) {
                    transformBodyTo(NotModifiedContent)
                }
            }
        }

        fun tagOf(bytes: ByteArray): String {
            val digest: ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
            return digest.take(12).joinToString(separator = "", prefix = "\"", postfix = "\"") { "%02x".format(it) }
        }

        fun gzip(bytes: ByteArray): ByteArray {
            val out: ByteArrayOutputStream = ByteArrayOutputStream()
            GZIPOutputStream(out).use { it.write(bytes) }
            return out.toByteArray()
        }
    }
}
