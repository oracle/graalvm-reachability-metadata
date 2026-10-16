/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_websocket_serialization_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.converter
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.sendSerialized
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.receiveDeserialized
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.webSocket
import io.ktor.util.reflect.typeInfo
import io.ktor.websocket.CloseReason
import io.ktor.websocket.WebSocketDeflateExtension
import io.ktor.websocket.close
import io.ktor.websocket.extensionOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.zip.Deflater
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.websocket.WebSockets as ServerWebSockets

/**
 * Typed messages through the permessage-deflate WebSocket extension, where `sendSerialized`
 * and `receiveDeserialized` with the kotlinx JSON converter run on sessions whose frames the
 * negotiated `WebSocketDeflateExtension` compresses between a CIO server and a CIO client.
 *
 * A catalog service streams large product pages to a storefront that asks for them by
 * category; it reports whether compression was negotiated, and serves an uncompressed client
 * the same typed pages.
 */
public class CompressedCatalogSyncTest {
    /** The storefront's request for one category of the catalog. */
    @Serializable
    data class PageRequest(val category: String, val page: Int)

    /** One catalog entry; descriptions repeat, which is what makes deflate worthwhile. */
    @Serializable
    data class Product(val sku: String, val name: String, val description: String, val priceCents: Long, val tags: List<String>)

    /** A page of products, large enough to cross the compression threshold. */
    @Serializable
    data class CatalogPage(val category: String, val page: Int, val products: List<Product>, val last: Boolean)

    /** The service's closing summary of one sync session. */
    @Serializable
    data class SyncSummary(val pagesServed: Int, val productsServed: Int, val compressed: Boolean)

    private lateinit var server: EmbeddedServer<*, *>
    private var port: Int = 0

    @BeforeEach
    fun startCatalogService() {
        server = embeddedServer(ServerCIO, port = 0, host = HOST) {
            install(ServerWebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(CATALOG_JSON)
                extensions {
                    install(WebSocketDeflateExtension) {
                        compressionLevel = Deflater.BEST_COMPRESSION
                        serverNoContextTakeOver = true
                        compressIfBiggerThan(COMPRESSION_THRESHOLD)
                    }
                }
            }
            routing {
                webSocket("/catalog") {
                    var pagesServed = 0
                    var productsServed = 0
                    while (true) {
                        val request: PageRequest = receiveDeserialized<PageRequest>()
                        val page: CatalogPage = catalogPage(request.category, request.page)
                        sendSerialized(page)
                        pagesServed += 1
                        productsServed += page.products.size
                        if (page.last) {
                            break
                        }
                    }
                    val compressed: Boolean = extensionOrNull(WebSocketDeflateExtension) != null
                    sendSerialized(SyncSummary(pagesServed, productsServed, compressed))
                    close(CloseReason(CloseReason.Codes.NORMAL, "catalog synced"))
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().single().port }
    }

    @AfterEach
    fun stopCatalogService() {
        server.stop(gracePeriodMillis = 100, timeoutMillis = 5_000)
    }

    @Test
    fun `storefront syncs a category over a deflate-compressed session`(): Unit = runBlocking {
        val client = HttpClient(ClientCIO) {
            install(ClientWebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(CATALOG_JSON)
                extensions {
                    install(WebSocketDeflateExtension) {
                        clientNoContextTakeOver = true
                        compressIfBiggerThan(COMPRESSION_THRESHOLD)
                    }
                }
            }
        }
        val pages: MutableList<CatalogPage> = mutableListOf()
        var summary: SyncSummary? = null
        var clientNegotiated: WebSocketDeflateExtension? = null
        var observedClose: CloseReason? = null

        client.use { storefront: HttpClient ->
            withTimeout(TIMEOUT_MILLIS) {
                storefront.webSocket(host = HOST, port = port, path = "/catalog") {
                    clientNegotiated = extensionOrNull(WebSocketDeflateExtension)
                    assertInstanceOf(KotlinxWebsocketSerializationConverter::class.java, converter)
                    var pageNumber = 1
                    do {
                        sendSerialized(PageRequest("garden", pageNumber))
                        val page: CatalogPage = receiveDeserialized(typeInfo<CatalogPage>())
                        pages += page
                        pageNumber += 1
                    } while (!page.last)
                    summary = receiveDeserialized<SyncSummary>()
                    observedClose = closeReason.await()
                }
            }
        }

        assertNotNull(clientNegotiated)
        assertEquals((1..PAGES_PER_CATEGORY).map { page: Int -> catalogPage("garden", page) }, pages)
        assertEquals(SyncSummary(PAGES_PER_CATEGORY, PAGES_PER_CATEGORY * PRODUCTS_PER_PAGE, compressed = true), summary)
        assertEquals("trowel-garden-1-0", pages.first().products.first().sku)
        assertEquals(listOf("garden", "outdoor", "page-3"), pages.last().products.last().tags)
        assertEquals(CloseReason(CloseReason.Codes.NORMAL, "catalog synced"), observedClose)
    }

    @Test
    fun `a client without the extension receives the same typed pages uncompressed`(): Unit = runBlocking {
        val client = HttpClient(ClientCIO) {
            install(ClientWebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(CATALOG_JSON)
            }
        }
        val pages: MutableList<CatalogPage> = mutableListOf()
        var summary: SyncSummary? = null
        var clientNegotiated: WebSocketDeflateExtension? = null

        client.use { storefront: HttpClient ->
            withTimeout(TIMEOUT_MILLIS) {
                storefront.webSocket(host = HOST, port = port, path = "/catalog") {
                    clientNegotiated = extensionOrNull(WebSocketDeflateExtension)
                    for (pageNumber: Int in 1..PAGES_PER_CATEGORY) {
                        sendSerialized(PageRequest("kitchen", pageNumber))
                        pages += receiveDeserialized<CatalogPage>()
                    }
                    summary = receiveDeserialized<SyncSummary>()
                    closeReason.await()
                }
            }
        }

        assertNull(clientNegotiated)
        assertEquals((1..PAGES_PER_CATEGORY).map { page: Int -> catalogPage("kitchen", page) }, pages)
        assertEquals(SyncSummary(PAGES_PER_CATEGORY, PAGES_PER_CATEGORY * PRODUCTS_PER_PAGE, compressed = false), summary)
    }

    private companion object {
        const val HOST: String = "127.0.0.1"
        const val TIMEOUT_MILLIS: Long = 30_000
        const val COMPRESSION_THRESHOLD: Int = 512
        const val PAGES_PER_CATEGORY: Int = 3
        const val PRODUCTS_PER_PAGE: Int = 120
        val CATALOG_JSON: Json = Json { encodeDefaults = true }

        /** Builds the deterministic page the service holds for a category. */
        fun catalogPage(category: String, page: Int): CatalogPage {
            val products: List<Product> = (0 until PRODUCTS_PER_PAGE).map { index: Int ->
                val kind: String = listOf("trowel", "rake", "planter", "hose")[index % 4]
                Product(
                    sku = "$kind-$category-$page-$index",
                    name = "${kind.replaceFirstChar { it.uppercase() }} No. $index",
                    description = "A durable $kind for the $category, weather resistant and easy to clean.",
                    priceCents = 499L + index * 25L + page * 1_000L,
                    tags = listOf(category, if (category == "garden") "outdoor" else "indoor", "page-$page"),
                )
            }
            return CatalogPage(category, page, products, last = page == PAGES_PER_CATEGORY)
        }
    }
}
