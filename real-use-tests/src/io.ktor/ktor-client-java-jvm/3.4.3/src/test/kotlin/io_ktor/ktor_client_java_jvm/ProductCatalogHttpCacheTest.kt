/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_java_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.cache.storage.FileStorage
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * HTTP response caching through the client HttpCache plugin over the Java engine, as a storefront
 * caches product-catalog responses: max-age freshness, ETag and Last-Modified revalidation,
 * Vary-keyed variants, no-store and private responses, and a file-backed cache across restarts.
 */
public class ProductCatalogHttpCacheTest {

    /** The product catalog service; counts the requests that actually reach it per route. */
    class ProductCatalog : AutoCloseable {
        private val hits: MutableMap<String, AtomicInteger> = ConcurrentHashMap()

        /** The `If-None-Match` and `If-Modified-Since` values the catalog received, in order. */
        val validators: MutableList<String> = CopyOnWriteArrayList()

        /** The current price-list revision, exposed as its entity tag. */
        @Volatile
        var priceRevision: Int = 1

        private val server: EmbeddedServer<*, *> = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/products/{id}") {
                    val hit: Int = count(call)
                    call.response.header(HttpHeaders.CacheControl, "public, max-age=600")
                    call.respondText("product ${call.parameters["id"]} served #$hit")
                }
                get("/prices") {
                    val hit: Int = count(call)
                    val etag: String = "\"prices-r$priceRevision\""
                    val ifNoneMatch: String? = call.request.headers[HttpHeaders.IfNoneMatch]
                    ifNoneMatch?.let { validators += "If-None-Match: $it" }
                    call.response.header(HttpHeaders.CacheControl, "no-cache")
                    call.response.header(HttpHeaders.ETag, etag)
                    if (ifNoneMatch == etag) {
                        call.respond(HttpStatusCode.NotModified)
                    } else {
                        call.respondText("price list r$priceRevision served #$hit")
                    }
                }
                get("/stock") {
                    val hit: Int = count(call)
                    val ifModifiedSince: String? = call.request.headers[HttpHeaders.IfModifiedSince]
                    ifModifiedSince?.let { validators += "If-Modified-Since: $it" }
                    call.response.header(HttpHeaders.CacheControl, "no-cache")
                    call.response.header(HttpHeaders.LastModified, STOCK_LAST_MODIFIED)
                    if (ifModifiedSince == STOCK_LAST_MODIFIED) {
                        call.respond(HttpStatusCode.NotModified)
                    } else {
                        call.respondText("stock levels served #$hit")
                    }
                }
                get("/catalog") {
                    val hit: Int = count(call)
                    val language: String = call.request.headers[HttpHeaders.AcceptLanguage] ?: "en"
                    call.response.header(HttpHeaders.CacheControl, "max-age=600")
                    call.response.header(HttpHeaders.Vary, HttpHeaders.AcceptLanguage)
                    call.respondText("catalog [$language] served #$hit")
                }
                get("/cart") {
                    val hit: Int = count(call)
                    call.response.header(HttpHeaders.CacheControl, "no-store")
                    call.respondText("cart served #$hit")
                }
                get("/account") {
                    val hit: Int = count(call)
                    call.response.header(HttpHeaders.CacheControl, "private, max-age=600")
                    call.respondText("account served #$hit")
                }
            }
        }

        val port: Int

        init {
            server.start(wait = false)
            port = runBlocking { server.engine.resolvedConnectors().first().port }
        }

        private fun count(call: ApplicationCall): Int =
            hits.computeIfAbsent(call.request.local.uri.substringBefore('?')) { AtomicInteger() }.incrementAndGet()

        /** How many requests for [path] reached the catalog. */
        fun hitsOn(path: String): Int = hits[path]?.get() ?: 0

        override fun close() {
            server.stop(gracePeriodMillis = 0, timeoutMillis = 2_000)
        }
    }

    /** A storefront client that caches catalog responses and counts the ones served from cache. */
    class Storefront(catalog: ProductCatalog, configure: HttpCache.Config.() -> Unit = {}) : AutoCloseable {
        val servedFromCache: AtomicInteger = AtomicInteger()

        val client: HttpClient = HttpClient(Java) {
            defaultRequest { url("http://127.0.0.1:${catalog.port}/") }
            install(HttpCache) { configure() }
        }

        init {
            client.monitor.subscribe(HttpCache.HttpResponseFromCache) { servedFromCache.incrementAndGet() }
        }

        suspend fun fetch(path: String, language: String? = null): String {
            val response: HttpResponse = client.get(path) {
                language?.let { header(HttpHeaders.AcceptLanguage, it) }
            }
            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            return response.bodyAsText()
        }

        override fun close() {
            client.close()
        }
    }

    @Test
    fun servesFreshResponsesFromCacheUntilMaxAgeExpires(): Unit = runBlocking {
        ProductCatalog().use { catalog: ProductCatalog ->
            Storefront(catalog).use { storefront: Storefront ->
                assertThat(storefront.fetch("products/42")).isEqualTo("product 42 served #1")
                assertThat(storefront.fetch("products/42")).isEqualTo("product 42 served #1")
                assertThat(storefront.fetch("products/42")).isEqualTo("product 42 served #1")
                assertThat(catalog.hitsOn("/products/42")).isEqualTo(1)
                assertThat(storefront.servedFromCache.get()).isEqualTo(2)

                // Each URL is its own cache entry.
                assertThat(storefront.fetch("products/7")).isEqualTo("product 7 served #1")
                assertThat(catalog.hitsOn("/products/7")).isEqualTo(1)

                // A no-store cart is fetched from the service every time.
                assertThat(storefront.fetch("cart")).isEqualTo("cart served #1")
                assertThat(storefront.fetch("cart")).isEqualTo("cart served #2")
                assertThat(catalog.hitsOn("/cart")).isEqualTo(2)
                assertThat(storefront.servedFromCache.get()).isEqualTo(2)
            }
        }
    }

    @Test
    fun revalidatesWithEntityTagsAndLastModifiedDates(): Unit = runBlocking {
        ProductCatalog().use { catalog: ProductCatalog ->
            Storefront(catalog).use { storefront: Storefront ->
                assertThat(storefront.fetch("prices")).isEqualTo("price list r1 served #1")
                // The service answers 304 and the cache supplies the stored body.
                assertThat(storefront.fetch("prices")).isEqualTo("price list r1 served #1")
                assertThat(catalog.hitsOn("/prices")).isEqualTo(2)
                assertThat(storefront.servedFromCache.get()).isEqualTo(1)

                // A new revision no longer matches, so the fresh body replaces the cached one.
                catalog.priceRevision = 2
                assertThat(storefront.fetch("prices")).isEqualTo("price list r2 served #3")
                assertThat(storefront.fetch("prices")).isEqualTo("price list r2 served #3")
                assertThat(catalog.hitsOn("/prices")).isEqualTo(4)

                assertThat(storefront.fetch("stock")).isEqualTo("stock levels served #1")
                assertThat(storefront.fetch("stock")).isEqualTo("stock levels served #1")
                assertThat(catalog.hitsOn("/stock")).isEqualTo(2)
                assertThat(storefront.servedFromCache.get()).isEqualTo(3)

                assertThat(catalog.validators).containsExactly(
                    "If-None-Match: \"prices-r1\"",
                    "If-None-Match: \"prices-r1\"",
                    "If-None-Match: \"prices-r2\"",
                    "If-Modified-Since: $STOCK_LAST_MODIFIED",
                )
            }
        }
    }

    @Test
    fun keepsOneVariantPerVaryHeaderValue(): Unit = runBlocking {
        ProductCatalog().use { catalog: ProductCatalog ->
            Storefront(catalog).use { storefront: Storefront ->
                assertThat(storefront.fetch("catalog", language = "en")).isEqualTo("catalog [en] served #1")
                assertThat(storefront.fetch("catalog", language = "de")).isEqualTo("catalog [de] served #2")
                assertThat(storefront.fetch("catalog", language = "en")).isEqualTo("catalog [en] served #1")
                assertThat(storefront.fetch("catalog", language = "de")).isEqualTo("catalog [de] served #2")
                assertThat(catalog.hitsOn("/catalog")).isEqualTo(2)
                assertThat(storefront.servedFromCache.get()).isEqualTo(2)
            }
        }
    }

    @Test
    fun cachesPrivateResponsesOnlyInAPrivateCache(): Unit = runBlocking {
        ProductCatalog().use { catalog: ProductCatalog ->
            Storefront(catalog) { isShared = true }.use { edgeProxy: Storefront ->
                assertThat(edgeProxy.fetch("account")).isEqualTo("account served #1")
                assertThat(edgeProxy.fetch("account")).isEqualTo("account served #2")
                assertThat(edgeProxy.servedFromCache.get()).isZero()
            }
            Storefront(catalog).use { browser: Storefront ->
                assertThat(browser.fetch("account")).isEqualTo("account served #3")
                assertThat(browser.fetch("account")).isEqualTo("account served #3")
                assertThat(browser.servedFromCache.get()).isEqualTo(1)
            }
            assertThat(catalog.hitsOn("/account")).isEqualTo(3)
        }
    }

    @Test
    fun reusesAFileBackedCacheAcrossClientRestarts(@TempDir cacheDirectory: File): Unit = runBlocking {
        ProductCatalog().use { catalog: ProductCatalog ->
            Storefront(catalog) { publicStorage(FileStorage(cacheDirectory)) }.use { storefront: Storefront ->
                assertThat(storefront.fetch("products/42")).isEqualTo("product 42 served #1")
                assertThat(storefront.fetch("prices")).isEqualTo("price list r1 served #1")
            }
            assertThat(cacheDirectory.listFiles()).isNotEmpty()

            Storefront(catalog) { publicStorage(FileStorage(cacheDirectory)) }.use { restarted: Storefront ->
                assertThat(restarted.fetch("products/42")).isEqualTo("product 42 served #1")
                assertThat(restarted.fetch("prices")).isEqualTo("price list r1 served #1")
                assertThat(restarted.servedFromCache.get()).isEqualTo(2)
            }
            assertThat(catalog.hitsOn("/products/42")).isEqualTo(1)
            assertThat(catalog.hitsOn("/prices")).isEqualTo(2)
            assertThat(catalog.validators).containsExactly("If-None-Match: \"prices-r1\"")
        }
    }

    private companion object {
        const val STOCK_LAST_MODIFIED: String = "Wed, 01 Oct 2025 08:00:00 GMT"
    }
}
