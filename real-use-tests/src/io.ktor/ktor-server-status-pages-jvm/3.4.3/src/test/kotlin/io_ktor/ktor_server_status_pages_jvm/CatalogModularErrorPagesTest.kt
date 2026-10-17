/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_status_pages_jvm

import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.io.IOException
import java.util.Collections
import kotlin.reflect.KClass
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * A `StatusPages` configuration composed from several modules' `StatusPagesConfig`
 * extensions, where a product catalog layers its own error pages over platform defaults.
 *
 * Each module registers exception handlers, status handlers, and the unhandled-call
 * fallback; for the same exception type, status code, or fallback the registration
 * made last replaces the earlier one, while everything else both modules registered
 * stays in force. A third module registers handlers from a table of exception classes.
 */
public class CatalogModularErrorPagesTest {
    /** A product the catalog no longer sells. */
    class ProductRetired(val sku: String) : RuntimeException("$sku has been retired")

    /** The upstream price feed failed to answer. */
    class PriceFeedUnavailable(val sku: String) : IOException("no price for $sku")

    /** One row of the catalog's error table: a failure type and how to answer it. */
    class ErrorMapping<T : Throwable>(
        private val type: KClass<T>,
        private val status: HttpStatusCode,
        private val code: String,
    ) {
        /** Registers this row as an exception handler that records [code] in [handled]. */
        fun register(config: StatusPagesConfig, handled: MutableList<String>) {
            config.exception(type) { call, cause ->
                handled += "table:$code"
                call.respondText("$code: ${cause.message}", status = status)
            }
        }
    }

    /** The organisation-wide defaults every service starts from. */
    private fun StatusPagesConfig.platformErrorPages(handled: MutableList<String>) {
        exception<Throwable> { call, cause ->
            handled += "platform:internal"
            call.respondText("internal error (${cause::class.simpleName})", status = HttpStatusCode.InternalServerError)
        }
        exception<IllegalArgumentException> { call, cause ->
            handled += "platform:bad-request"
            call.respondText("bad request: ${cause.message}", status = HttpStatusCode.BadRequest)
        }
        status(HttpStatusCode.Forbidden, HttpStatusCode.ServiceUnavailable) { call, status ->
            handled += "platform:status-${status.value}"
            call.respondText("platform page for ${status.value}", status = status)
        }
        unhandled { call ->
            handled += "platform:no-route"
            call.respondText("no route for ${call.request.uri}", status = HttpStatusCode.NotFound)
        }
    }

    /** The catalog team's refinements of the platform defaults. */
    private fun StatusPagesConfig.catalogErrorPages(handled: MutableList<String>) {
        exception<IllegalArgumentException> { call, cause ->
            handled += "catalog:invalid-query"
            call.respondText("invalid catalog query: ${cause.message}", status = HttpStatusCode.UnprocessableEntity)
        }
        status(HttpStatusCode.ServiceUnavailable) { call, status ->
            handled += "catalog:status-${status.value}"
            call.respondText("the catalog is being restocked, try again shortly", status = status)
        }
        unhandled { call ->
            handled += "catalog:no-route"
            call.respondText("the catalog has no page at ${call.request.uri}", status = HttpStatusCode.NotFound)
        }
    }

    /** Installs the catalog with its error-page modules registered in [modules] order. */
    private fun ApplicationTestBuilder.catalog(
        modules: List<StatusPagesConfig.(MutableList<String>) -> Unit>,
    ): MutableList<String> {
        val handled: MutableList<String> = Collections.synchronizedList(mutableListOf())
        application {
            install(StatusPages) {
                modules.forEach { module -> module(handled) }
                ERROR_TABLE.forEach { mapping -> mapping.register(this, handled) }
            }
            routing {
                get("/products") {
                    val limit: Int? = call.request.queryParameters["limit"]?.toIntOrNull()
                    require(limit != null && limit in 1..100) { "limit must be between 1 and 100" }
                    call.respondText("first $limit of ${PRICES.size} products")
                }
                get("/products/{sku}") {
                    val sku: String = call.parameters["sku"]!!
                    if (sku in RETIRED) {
                        throw ProductRetired(sku)
                    }
                    val price: Int = PRICES[sku] ?: throw PriceFeedUnavailable(sku)
                    call.respondText("$sku costs $price cents")
                }
                get("/restock") {
                    call.respond(HttpStatusCode.ServiceUnavailable)
                }
                get("/admin") {
                    call.respond(HttpStatusCode.Forbidden)
                }
                get("/reports/margin") {
                    call.respondText("margin ${PRICES.getValue("LAMP-2") / (PRICES.size - PRICES.size)}")
                }
            }
        }
        return handled
    }

    @Test
    fun catalogRegistrationsMadeAfterThePlatformReplaceItsDefaults() = testApplication {
        val handled: MutableList<String> = catalog(
            listOf({ log -> platformErrorPages(log) }, { log -> catalogErrorPages(log) }),
        )

        val listing: HttpResponse = client.get("/products?limit=500")
        val restock: HttpResponse = client.get("/restock")
        val nowhere: HttpResponse = client.get("/checkout")

        assertEquals(HttpStatusCode.UnprocessableEntity, listing.status)
        assertEquals("invalid catalog query: limit must be between 1 and 100", listing.bodyAsText())
        assertEquals(HttpStatusCode.ServiceUnavailable, restock.status)
        assertEquals("the catalog is being restocked, try again shortly", restock.bodyAsText())
        assertEquals(HttpStatusCode.NotFound, nowhere.status)
        assertEquals("the catalog has no page at /checkout", nowhere.bodyAsText())
        assertEquals(listOf("catalog:invalid-query", "catalog:status-503", "catalog:no-route"), handled.toList())
    }

    @Test
    fun platformRegistrationsTheCatalogDoesNotRepeatStayInForce() = testApplication {
        val handled: MutableList<String> = catalog(
            listOf({ log -> platformErrorPages(log) }, { log -> catalogErrorPages(log) }),
        )

        val listing: HttpResponse = client.get("/products?limit=20")
        val admin: HttpResponse = client.get("/admin")
        val margin: HttpResponse = client.get("/reports/margin")

        assertEquals(HttpStatusCode.OK, listing.status)
        assertEquals("first 20 of 2 products", listing.bodyAsText())
        assertEquals(HttpStatusCode.Forbidden, admin.status)
        assertEquals("platform page for 403", admin.bodyAsText())
        assertEquals(HttpStatusCode.InternalServerError, margin.status)
        assertEquals("internal error (ArithmeticException)", margin.bodyAsText())
        assertEquals(listOf("platform:status-403", "platform:internal"), handled.toList())
    }

    @Test
    fun registeringThePlatformLastRestoresItsDefaults() = testApplication {
        val handled: MutableList<String> = catalog(
            listOf({ log -> catalogErrorPages(log) }, { log -> platformErrorPages(log) }),
        )

        val listing: HttpResponse = client.get("/products?limit=500")
        val restock: HttpResponse = client.get("/restock")
        val nowhere: HttpResponse = client.get("/checkout")

        assertEquals(HttpStatusCode.BadRequest, listing.status)
        assertEquals("bad request: limit must be between 1 and 100", listing.bodyAsText())
        assertEquals(HttpStatusCode.ServiceUnavailable, restock.status)
        assertEquals("platform page for 503", restock.bodyAsText())
        assertEquals(HttpStatusCode.NotFound, nowhere.status)
        assertEquals("no route for /checkout", nowhere.bodyAsText())
        assertEquals(listOf("platform:bad-request", "platform:status-503", "platform:no-route"), handled.toList())
    }

    @Test
    fun tableRegisteredHandlersAnswerTheirExceptionTypesWhicheverModulesRanFirst() = testApplication {
        val handled: MutableList<String> = catalog(
            listOf({ log -> catalogErrorPages(log) }, { log -> platformErrorPages(log) }),
        )

        val retired: HttpResponse = client.get("/products/DESK-9")
        val unpriced: HttpResponse = client.get("/products/CHAIR-4")
        val priced: HttpResponse = client.get("/products/LAMP-2")

        assertEquals(HttpStatusCode.Gone, retired.status)
        assertEquals("product-retired: DESK-9 has been retired", retired.bodyAsText())
        assertEquals(HttpStatusCode.BadGateway, unpriced.status)
        assertEquals("price-feed: no price for CHAIR-4", unpriced.bodyAsText())
        assertEquals(HttpStatusCode.OK, priced.status)
        assertEquals("LAMP-2 costs 1899 cents", priced.bodyAsText())
        assertEquals(listOf("table:product-retired", "table:price-feed"), handled.toList())
    }

    private companion object {
        val PRICES: Map<String, Int> = mapOf("LAMP-2" to 1899, "RUG-7" to 4500)

        val RETIRED: Set<String> = setOf("DESK-9")

        val ERROR_TABLE: List<ErrorMapping<*>> = listOf(
            ErrorMapping(ProductRetired::class, HttpStatusCode.Gone, "product-retired"),
            ErrorMapping(PriceFeedUnavailable::class, HttpStatusCode.BadGateway, "price-feed"),
        )
    }
}
