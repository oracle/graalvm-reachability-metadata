/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_apache5_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.cache.storage.CacheStorage
import io.ktor.client.plugins.cache.storage.FileStorage
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import io.ktor.server.cio.CIO as ServerCIO

/**
 * HTTP response caching with the `HttpCache` plugin on the `Apache5` engine.
 *
 * A storefront client reads a product catalog whose endpoints carry different caching rules:
 * categories are fresh for an hour and served from the cache, prices must be revalidated with
 * their `ETag` so an unchanged price list comes back as `304 Not Modified` while a repriced one
 * is downloaded again, and live stock levels are marked `no-store` so every read reaches the
 * service. A file-backed cache lets a restarted client reuse what an earlier one downloaded.
 */
class ProductCatalogResponseCacheTest {

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0
    private val categoryHits: AtomicInteger = AtomicInteger()
    private val priceHits: AtomicInteger = AtomicInteger()
    private val stockHits: AtomicInteger = AtomicInteger()
    private val priceListVersion: AtomicInteger = AtomicInteger(1)
    private val priceValidators: MutableList<String?> = CopyOnWriteArrayList()

    @BeforeEach
    fun startServer() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/catalog/categories") {
                    categoryHits.incrementAndGet()
                    call.response.header(HttpHeaders.CacheControl, "public, max-age=3600")
                    call.respondText("garden,kitchen,tools")
                }
                get("/catalog/prices") {
                    priceHits.incrementAndGet()
                    val version: Int = priceListVersion.get()
                    val etag: String = "\"prices-v$version\""
                    priceValidators.add(call.request.headers[HttpHeaders.IfNoneMatch])
                    call.response.header(HttpHeaders.CacheControl, "no-cache")
                    call.response.header(HttpHeaders.ETag, etag)
                    if (call.request.headers[HttpHeaders.IfNoneMatch] == etag) {
                        call.respond(HttpStatusCode.NotModified)
                    } else {
                        call.respondText("rake=${10 + version},kettle=${20 + version}")
                    }
                }
                get("/catalog/stock") {
                    val hit: Int = stockHits.incrementAndGet()
                    call.response.header(HttpHeaders.CacheControl, "no-store")
                    call.respondText("rake=${100 - hit}")
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stopServer() {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    @Test
    fun freshCategoriesAreServedFromCacheWithoutReachingTheService() = runBlocking {
        val cachedUrls: MutableList<String> = CopyOnWriteArrayList()
        HttpClient(Apache5) {
            install(HttpCache)
        }.use { client: HttpClient ->
            client.monitor.subscribe(HttpCache.HttpResponseFromCache) { response: HttpResponse ->
                cachedUrls.add(response.call.request.url.encodedPath)
            }

            val first: HttpResponse = client.get("http://127.0.0.1:$port/catalog/categories")
            assertEquals(HttpStatusCode.OK, first.status)
            assertEquals("garden,kitchen,tools", first.bodyAsText())

            val second: HttpResponse = client.get("http://127.0.0.1:$port/catalog/categories")
            assertEquals(HttpStatusCode.OK, second.status)
            assertEquals("garden,kitchen,tools", second.bodyAsText())
            assertEquals("public, max-age=3600", second.headers[HttpHeaders.CacheControl])

            assertEquals(1, categoryHits.get())
            assertEquals(listOf("/catalog/categories"), cachedUrls)
        }
    }

    @Test
    fun pricesAreRevalidatedWithTheirEntityTag() = runBlocking {
        val cachedUrls: MutableList<String> = CopyOnWriteArrayList()
        HttpClient(Apache5) {
            install(HttpCache) {
                privateStorage(CacheStorage.Unlimited())
            }
        }.use { client: HttpClient ->
            client.monitor.subscribe(HttpCache.HttpResponseFromCache) { response: HttpResponse ->
                cachedUrls.add(response.call.request.url.encodedPath)
            }
            val prices: String = "http://127.0.0.1:$port/catalog/prices"

            val downloaded: HttpResponse = client.get(prices)
            assertEquals("rake=11,kettle=21", downloaded.bodyAsText())
            assertEquals("\"prices-v1\"", downloaded.headers[HttpHeaders.ETag])

            val revalidated: HttpResponse = client.get(prices)
            assertEquals(HttpStatusCode.OK, revalidated.status)
            assertEquals("rake=11,kettle=21", revalidated.bodyAsText())
            assertEquals(listOf("/catalog/prices"), cachedUrls)

            priceListVersion.set(2)
            val repriced: HttpResponse = client.get(prices)
            assertEquals(HttpStatusCode.OK, repriced.status)
            assertEquals("rake=12,kettle=22", repriced.bodyAsText())
            assertEquals("\"prices-v2\"", repriced.headers[HttpHeaders.ETag])

            assertEquals(3, priceHits.get())
            assertEquals(listOf(null, "\"prices-v1\"", "\"prices-v1\""), priceValidators)
            assertEquals(1, cachedUrls.size)
        }
    }

    @Test
    fun liveStockMarkedNoStoreAlwaysReachesTheService() = runBlocking {
        HttpClient(Apache5) {
            install(HttpCache)
        }.use { client: HttpClient ->
            val stock: String = "http://127.0.0.1:$port/catalog/stock"

            assertEquals("rake=99", client.get(stock).bodyAsText())
            assertEquals("rake=98", client.get(stock).bodyAsText())
            assertEquals("rake=97", client.get(stock).bodyAsText())

            assertEquals(3, stockHits.get())
        }
    }

    @Test
    fun restartedClientReusesCatalogFromFileBackedCache(@TempDir cacheDirectory: File) = runBlocking {
        val categories: String = "http://127.0.0.1:$port/catalog/categories"
        HttpClient(Apache5) {
            install(HttpCache) {
                publicStorage(FileStorage(cacheDirectory))
            }
        }.use { client: HttpClient ->
            assertEquals("garden,kitchen,tools", client.get(categories).bodyAsText())
        }
        assertEquals(1, categoryHits.get())
        assertTrue(cacheDirectory.listFiles()!!.isNotEmpty())

        var servedFromCache: HttpResponse? = null
        HttpClient(Apache5) {
            install(HttpCache) {
                publicStorage(FileStorage(cacheDirectory))
            }
        }.use { restarted: HttpClient ->
            restarted.monitor.subscribe(HttpCache.HttpResponseFromCache) { response: HttpResponse ->
                servedFromCache = response
            }
            assertNull(servedFromCache)

            val reread: HttpResponse = restarted.get(categories)
            assertEquals(HttpStatusCode.OK, reread.status)
            assertEquals("garden,kitchen,tools", reread.bodyAsText())
            assertEquals("/catalog/categories", servedFromCache!!.call.request.url.encodedPath)
        }
        assertEquals(1, categoryHits.get())
    }
}
