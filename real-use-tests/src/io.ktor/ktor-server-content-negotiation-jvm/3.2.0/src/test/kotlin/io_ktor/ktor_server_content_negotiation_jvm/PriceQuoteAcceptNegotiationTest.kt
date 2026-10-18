/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_content_negotiation_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.serialization.kotlinx.xml.DefaultXml
import io.ktor.serialization.kotlinx.xml.xml
import io.ktor.server.application.install
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.contentnegotiation.ContentTypeWithQuality
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.ktor.server.cio.CIO as ServerCIO

/**
 * Response converter selection driven by the request's `Accept` header.
 *
 * A price-quote service registers a JSON and an XML converter and lets callers force a
 * format with a `?format=` query parameter through an `accept` contributor, as services that
 * must serve browsers and legacy integrations do. It also enables Accept-header compliance so
 * a caller asking only for a format the service cannot produce gets `406 Not Acceptable`.
 * The test checks that quality values, not header order, pick the converter, that a wildcard
 * falls back to the first registered converter, that the query override wins over the
 * header, and that each body decodes back to the quote the route produced.
 */
class PriceQuoteAcceptNegotiationTest {

    @Serializable
    data class Quote(val sku: String, val currency: String, val unitPriceCents: Long, val tiers: List<Int>)

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private lateinit var client: HttpClient
    private var port: Int = 0

    @BeforeEach
    fun start() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            install(ContentNegotiation) {
                json()
                xml()
                checkAcceptHeaderCompliance = true
                accept { call, acceptItems: List<ContentTypeWithQuality> ->
                    when (call.request.queryParameters["format"]) {
                        "xml" -> listOf(ContentTypeWithQuality(ContentType.Application.Xml))
                        "json" -> listOf(ContentTypeWithQuality(ContentType.Application.Json))
                        else -> acceptItems
                    }
                }
            }
            routing {
                get("/quotes/{sku}") {
                    val sku: String = call.parameters["sku"]!!
                    val quote = QUOTES[sku]
                    if (quote == null) {
                        call.respond(HttpStatusCode.NotFound)
                    } else {
                        call.respond(quote)
                    }
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
        client = HttpClient(CIO)
    }

    @AfterEach
    fun stop() {
        client.close()
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    @Test
    fun acceptHeaderQualityPicksTheConverter() = runBlocking {
        val preferJson: HttpResponse = fetch("/quotes/valve-22", "application/xml;q=0.4, application/json;q=0.9")
        assertEquals(HttpStatusCode.OK, preferJson.status)
        assertFormat(ContentType.Application.Json, preferJson)
        assertEquals(QUOTES["valve-22"], Json.decodeFromString<Quote>(preferJson.bodyAsText()))

        val preferXml: HttpResponse = fetch("/quotes/valve-22", "application/json;q=0.2, application/xml")
        assertEquals(HttpStatusCode.OK, preferXml.status)
        assertFormat(ContentType.Application.Xml, preferXml)
        val xmlBody: String = preferXml.bodyAsText()
        assertTrue(xmlBody.contains("valve-22"), xmlBody)
        assertEquals(QUOTES["valve-22"], DefaultXml.decodeFromString(Quote.serializer(), xmlBody))
    }

    @Test
    fun wildcardFallsBackToTheFirstRegisteredConverter() = runBlocking {
        val anything: HttpResponse = fetch("/quotes/pump-7", "*/*")
        assertEquals(HttpStatusCode.OK, anything.status)
        assertFormat(ContentType.Application.Json, anything)
        assertEquals(QUOTES["pump-7"], Json.decodeFromString<Quote>(anything.bodyAsText()))

        val anyApplication: HttpResponse = fetch("/quotes/pump-7", "text/html;q=0.9, application/*;q=0.8")
        assertEquals(HttpStatusCode.OK, anyApplication.status)
        assertFormat(ContentType.Application.Json, anyApplication)
    }

    @Test
    fun formatQueryParameterOverridesTheAcceptHeader() = runBlocking {
        val forcedXml: HttpResponse = fetch("/quotes/pump-7?format=xml", "application/json")
        assertEquals(HttpStatusCode.OK, forcedXml.status)
        assertFormat(ContentType.Application.Xml, forcedXml)
        assertEquals(QUOTES["pump-7"], DefaultXml.decodeFromString(Quote.serializer(), forcedXml.bodyAsText()))

        val forcedJson: HttpResponse = fetch("/quotes/pump-7?format=json", "application/xml")
        assertEquals(HttpStatusCode.OK, forcedJson.status)
        assertFormat(ContentType.Application.Json, forcedJson)
        assertEquals(QUOTES["pump-7"], Json.decodeFromString<Quote>(forcedJson.bodyAsText()))
    }

    @Test
    fun unsupportedFormatIsNotAcceptable() = runBlocking {
        val csvOnly: HttpResponse = fetch("/quotes/valve-22", "text/csv")
        assertEquals(HttpStatusCode.NotAcceptable, csvOnly.status)

        val missing: HttpResponse = fetch("/quotes/unknown", "application/json")
        assertEquals(HttpStatusCode.NotFound, missing.status)
    }

    private suspend fun fetch(path: String, accept: String): HttpResponse =
        client.get("http://127.0.0.1:$port$path") { header(HttpHeaders.Accept, accept) }

    private fun assertFormat(expected: ContentType, response: HttpResponse) {
        val actual: ContentType? = response.contentType()
        assertTrue(actual != null && actual.match(expected), "expected $expected, got $actual")
    }

    private companion object {
        val QUOTES: Map<String, Quote> = mapOf(
            "valve-22" to Quote("valve-22", "EUR", 1_299, listOf(1, 10, 100)),
            "pump-7" to Quote("pump-7", "USD", 48_500, listOf(1, 5)),
        )
    }
}
