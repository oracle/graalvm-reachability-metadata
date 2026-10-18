/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_core_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.cache.storage.CacheStorage
import io.ktor.client.plugins.cache.storage.CachedResponseData
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import io.ktor.server.cio.CIO as ServerCIO

/**
 * The client `HttpCache` plugin honoring HTTP caching headers.
 *
 * A storefront reads its product catalog, exchange rates and shopping cart from a backend.
 * The test checks that a `max-age` response is answered from the client cache without a
 * second request, that a `no-cache` response carrying an `ETag` is revalidated with
 * `If-None-Match` and its cached body reused when the server answers `304 Not Modified`,
 * that a `no-store` response is never cached, and that cache hits are announced through
 * the `HttpResponseFromCache` monitor event and land in the configured public storage.
 */
class ProductCatalogHttpCacheTest {

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0
    private val hits: ConcurrentHashMap<String, AtomicInteger> = ConcurrentHashMap()
    private val revalidations: CopyOnWriteArrayList<String> = CopyOnWriteArrayList()

    private fun hit(path: String): Int = hits.computeIfAbsent(path) { AtomicInteger() }.incrementAndGet()

    private fun hitsOf(path: String): Int = hits[path]?.get() ?: 0

    @BeforeEach
    fun start() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/catalog") {
                    val count: Int = hit("/catalog")
                    call.response.header(HttpHeaders.CacheControl, "public, max-age=600")
                    call.respondText("catalog v$count: lamp, desk, chair")
                }
                get("/rates") {
                    hit("/rates")
                    call.response.header(HttpHeaders.CacheControl, "no-cache")
                    call.response.header(HttpHeaders.ETag, RATES_ETAG)
                    val ifNoneMatch: String? = call.request.headers[HttpHeaders.IfNoneMatch]
                    if (ifNoneMatch != null) {
                        revalidations.add(ifNoneMatch)
                    }
                    if (ifNoneMatch == RATES_ETAG) {
                        call.respond(HttpStatusCode.NotModified)
                    } else {
                        call.respondText("EUR=1.00 USD=1.08")
                    }
                }
                get("/cart") {
                    val count: Int = hit("/cart")
                    call.response.header(HttpHeaders.CacheControl, "no-store")
                    call.respondText("cart items=$count")
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stop() {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    @Test
    fun cacheHeadersDecideWhetherTheBackendIsAskedAgain() {
        val base: String = "http://127.0.0.1:$port"
        val storage: CacheStorage = CacheStorage.Unlimited()
        val client = HttpClient(CIO) {
            install(HttpCache) {
                publicStorage(storage)
            }
        }
        val servedFromCache: CopyOnWriteArrayList<String> = CopyOnWriteArrayList()
        client.monitor.subscribe(HttpCache.HttpResponseFromCache) { response: HttpResponse ->
            servedFromCache.add(response.call.request.url.encodedPath)
        }

        client.use {
            runBlocking {
                val first: String = it.get("$base/catalog").bodyAsText()
                val second: HttpResponse = it.get("$base/catalog")
                assertEquals("catalog v1: lamp, desk, chair", first)
                assertEquals(HttpStatusCode.OK, second.status)
                assertEquals(first, second.bodyAsText())
                assertEquals(1, hitsOf("/catalog"))

                val freshRates: String = it.get("$base/rates").bodyAsText()
                val revalidated: HttpResponse = it.get("$base/rates")
                assertEquals("EUR=1.00 USD=1.08", freshRates)
                assertEquals(freshRates, revalidated.bodyAsText())
                assertEquals(2, hitsOf("/rates"))
                assertEquals(listOf(RATES_ETAG), revalidations.toList())

                assertEquals("cart items=1", it.get("$base/cart").bodyAsText())
                assertEquals("cart items=2", it.get("$base/cart").bodyAsText())
                assertEquals(2, hitsOf("/cart"))

                val cachedCatalog: Set<CachedResponseData> = storage.findAll(Url("$base/catalog"))
                val cachedCart: Set<CachedResponseData> = storage.findAll(Url("$base/cart"))
                assertEquals(1, cachedCatalog.size)
                assertEquals("catalog v1: lamp, desk, chair", String(cachedCatalog.single().body))
                assertTrue(cachedCart.isEmpty())
            }
        }

        assertTrue(servedFromCache.contains("/catalog"), "catalog hits: $servedFromCache")
        assertTrue(servedFromCache.contains("/rates"), "rates hits: $servedFromCache")
        assertTrue(servedFromCache.none { path: String -> path == "/cart" }, "cart hits: $servedFromCache")
    }

    private companion object {
        const val RATES_ETAG: String = "\"rates-2026-10-18\""
    }
}
