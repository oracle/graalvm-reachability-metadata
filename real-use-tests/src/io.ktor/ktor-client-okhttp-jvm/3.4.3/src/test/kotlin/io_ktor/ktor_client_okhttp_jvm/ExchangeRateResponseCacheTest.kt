/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_okhttp_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Cache
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The OkHttp disk response cache that the OkHttp engine consults when its `config` block installs one.
 *
 * An exchange-rate client keeps quotes in an on-disk HTTP cache so that repeated lookups do not
 * hit the rate service. Daily reference rates are fresh for an hour and are served straight from
 * disk; live quotes must be revalidated on every lookup with their `ETag`, and the service answers
 * `304 Not Modified` while the quote has not moved. The test checks what the application sees
 * through Ktor (status, body, validators) and what reached the service: no request for a fresh
 * hit, a conditional request for a revalidation, and a full fetch when the quote changed or the
 * caller demanded a network read with `Cache-Control: no-cache`.
 */
class ExchangeRateResponseCacheTest {

    /** What one lookup returned to the application. */
    data class Quote(val status: HttpStatusCode, val body: String, val etag: String?)

    @TempDir
    lateinit var cacheDirectory: File

    private lateinit var rateService: MockWebServer
    private lateinit var cache: Cache

    @BeforeEach
    fun start() {
        rateService = MockWebServer()
        rateService.start()
        cache = Cache(cacheDirectory, CACHE_SIZE_BYTES)
    }

    @AfterEach
    fun stop() {
        cache.close()
        rateService.close()
    }

    @Test
    fun freshReferenceRatesAreServedFromDisk() {
        rateService.enqueue(
            MockResponse.Builder()
                .addHeader("Content-Type", "application/json")
                .addHeader(HttpHeaders.CacheControl, "public, max-age=3600")
                .body(REFERENCE_RATES)
                .build(),
        )

        val quotes: List<Quote> = rateClient().use { client: HttpClient ->
            runBlocking {
                List(3) { quote(client.get(rateService.url("/reference/EUR").toString())) }
            }
        }

        assertEquals(List(3) { Quote(HttpStatusCode.OK, REFERENCE_RATES, null) }, quotes)
        assertEquals(1, rateService.requestCount)
        assertEquals(3, cache.requestCount())
        assertEquals(1, cache.networkCount())
        assertEquals(2, cache.hitCount())
        assertEquals("GET /reference/EUR HTTP/1.1", takeRecorded().requestLine)
    }

    @Test
    fun liveQuotesAreRevalidatedWithTheirEntityTag() {
        rateService.enqueue(liveQuote(QUOTE_V1, "\"q-1\""))
        rateService.enqueue(
            MockResponse.Builder()
                .code(304)
                .addHeader(HttpHeaders.ETag, "\"q-1\"")
                .addHeader(HttpHeaders.CacheControl, "no-cache")
                .build(),
        )
        rateService.enqueue(liveQuote(QUOTE_V2, "\"q-2\""))

        val quotes: List<Quote> = rateClient().use { client: HttpClient ->
            runBlocking {
                List(3) { quote(client.get(rateService.url("/live/EURUSD").toString())) }
            }
        }

        assertEquals(
            listOf(
                Quote(HttpStatusCode.OK, QUOTE_V1, "\"q-1\""),
                Quote(HttpStatusCode.OK, QUOTE_V1, "\"q-1\""),
                Quote(HttpStatusCode.OK, QUOTE_V2, "\"q-2\""),
            ),
            quotes,
        )

        val initial: RecordedRequest = takeRecorded()
        val unchanged: RecordedRequest = takeRecorded()
        val moved: RecordedRequest = takeRecorded()
        assertNull(initial.headers[HttpHeaders.IfNoneMatch])
        assertEquals("\"q-1\"", unchanged.headers[HttpHeaders.IfNoneMatch])
        assertEquals("\"q-1\"", moved.headers[HttpHeaders.IfNoneMatch])

        assertEquals(3, cache.networkCount())
        assertEquals(1, cache.hitCount())
    }

    @Test
    fun callerCanBypassAFreshEntryWithNoCache() {
        rateService.enqueue(
            MockResponse.Builder()
                .addHeader("Content-Type", "application/json")
                .addHeader(HttpHeaders.CacheControl, "max-age=3600")
                .body(REFERENCE_RATES)
                .build(),
        )
        rateService.enqueue(
            MockResponse.Builder()
                .addHeader("Content-Type", "application/json")
                .addHeader(HttpHeaders.CacheControl, "max-age=3600")
                .body(REFERENCE_RATES_REVISED)
                .build(),
        )

        lateinit var cached: Quote
        lateinit var forced: Quote
        lateinit var afterForced: Quote
        rateClient().use { client: HttpClient ->
            runBlocking {
                val url: String = rateService.url("/reference/GBP").toString()
                cached = quote(client.get(url))
                forced = quote(client.get(url) { header(HttpHeaders.CacheControl, "no-cache") })
                afterForced = quote(client.get(url))
            }
        }

        assertEquals(Quote(HttpStatusCode.OK, REFERENCE_RATES, null), cached)
        assertEquals(Quote(HttpStatusCode.OK, REFERENCE_RATES_REVISED, null), forced)
        assertEquals(Quote(HttpStatusCode.OK, REFERENCE_RATES_REVISED, null), afterForced)
        assertEquals(2, rateService.requestCount)
        assertNull(takeRecorded().headers[HttpHeaders.CacheControl])
        assertEquals("no-cache", takeRecorded().headers[HttpHeaders.CacheControl])
        assertEquals(1, cache.hitCount())
    }

    /** Builds the rate client the way the application does: an OkHttp disk cache under Ktor. */
    private fun rateClient(): HttpClient = HttpClient(OkHttp) {
        engine {
            config {
                cache(cache)
            }
        }
    }

    private suspend fun quote(response: HttpResponse): Quote =
        Quote(response.status, response.bodyAsText(), response.headers[HttpHeaders.ETag])

    private fun liveQuote(body: String, etag: String): MockResponse = MockResponse.Builder()
        .addHeader("Content-Type", "application/json")
        .addHeader(HttpHeaders.CacheControl, "no-cache")
        .addHeader(HttpHeaders.ETag, etag)
        .body(body)
        .build()

    private fun takeRecorded(): RecordedRequest =
        checkNotNull(rateService.takeRequest(10, TimeUnit.SECONDS)) { "the rate service received no request" }

    private companion object {
        const val CACHE_SIZE_BYTES: Long = 1024L * 1024L
        const val REFERENCE_RATES: String = """{"base":"EUR","USD":"1.0842","GBP":"0.8571"}"""
        const val REFERENCE_RATES_REVISED: String = """{"base":"EUR","USD":"1.0851","GBP":"0.8566"}"""
        const val QUOTE_V1: String = """{"pair":"EURUSD","bid":"1.08417","ask":"1.08421"}"""
        const val QUOTE_V2: String = """{"pair":"EURUSD","bid":"1.08433","ask":"1.08437"}"""
    }
}
