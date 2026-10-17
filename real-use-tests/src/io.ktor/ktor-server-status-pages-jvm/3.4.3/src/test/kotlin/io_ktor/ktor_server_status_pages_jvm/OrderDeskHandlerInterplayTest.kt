/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_status_pages_jvm

import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.auth.principal
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.uri
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.util.Collections
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The interplay between exception and status handlers on one call, where an
 * order desk lets each handler answer a call at most once.
 *
 * A response written by an exception handler is never rewritten by the status
 * handler for its code, a status handler that throws hands its failure to the
 * matching exception handler, and a status page keeps the headers of the response
 * it replaces, such as the bearer challenge of a refused call.
 */
public class OrderDeskHandlerInterplayTest {
    /** An order id that the desk has no record of. */
    private class OrderNotFound(val id: String) : RuntimeException("order $id not found")

    /** The desk is closed for maintenance until [reopensAt]. */
    private class MaintenanceWindow(val reopensAt: String) : RuntimeException("closed until $reopensAt")

    /** Installs the order desk and returns the log of every handler invocation. */
    private fun ApplicationTestBuilder.orderDesk(): MutableList<String> {
        val handled: MutableList<String> = Collections.synchronizedList(mutableListOf())
        application {
            install(Authentication) {
                bearer("clerks") {
                    realm = "order-desk"
                    authenticate { credential -> CLERKS[credential.token]?.let { clerk -> UserIdPrincipal(clerk) } }
                }
            }
            install(StatusPages) {
                exception<OrderNotFound> { call, cause ->
                    handled += "exception:OrderNotFound"
                    call.respondText("no order ${cause.id} on file", status = HttpStatusCode.NotFound)
                }
                exception<MaintenanceWindow> { call, cause ->
                    handled += "exception:MaintenanceWindow"
                    call.response.header(HttpHeaders.RetryAfter, "3600")
                    call.respondText(
                        "order desk reopens at ${cause.reopensAt}",
                        status = HttpStatusCode.ServiceUnavailable,
                    )
                }
                status(HttpStatusCode.NotFound) { call, status ->
                    handled += "status:${status.value}"
                    call.respondText("nothing at ${call.request.uri}", status = status)
                }
                status(HttpStatusCode.Unauthorized) { call, status ->
                    handled += "status:${status.value}"
                    call.respondText("sign in as a clerk to continue", status = status)
                }
                status(HttpStatusCode.ServiceUnavailable) { _, status ->
                    handled += "status:${status.value}"
                    throw MaintenanceWindow("06:00")
                }
            }
            routing {
                get("/orders/{id}") {
                    val id: String = call.parameters["id"]!!
                    val order: String = ORDERS[id] ?: throw OrderNotFound(id)
                    call.respondText(order)
                }
                get("/checkout") {
                    call.respond(HttpStatusCode.ServiceUnavailable)
                }
                authenticate("clerks") {
                    get("/ledger") {
                        call.respondText("ledger opened by ${call.principal<UserIdPrincipal>()!!.name}")
                    }
                }
            }
        }
        return handled
    }

    @Test
    fun exceptionHandlerResponseIsNotRewrittenByStatusHandler() = testApplication {
        val handled: MutableList<String> = orderDesk()

        val found: HttpResponse = client.get("/orders/1001")
        val missing: HttpResponse = client.get("/orders/2002")

        assertEquals(HttpStatusCode.OK, found.status)
        assertEquals("2 x espresso beans", found.bodyAsText())
        assertEquals(HttpStatusCode.NotFound, missing.status)
        assertEquals("no order 2002 on file", missing.bodyAsText())
        assertEquals(listOf("exception:OrderNotFound"), handled.toList())
    }

    @Test
    fun plainNotFoundStillReachesStatusHandler() = testApplication {
        val handled: MutableList<String> = orderDesk()

        val response: HttpResponse = client.get("/invoices")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("nothing at /invoices", response.bodyAsText())
        assertEquals(listOf("status:404"), handled.toList())
    }

    @Test
    fun failingStatusHandlerHandsOverToExceptionHandler() = testApplication {
        val handled: MutableList<String> = orderDesk()

        val response: HttpResponse = client.get("/checkout")

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("order desk reopens at 06:00", response.bodyAsText())
        assertEquals("3600", response.headers[HttpHeaders.RetryAfter])
        assertEquals(listOf("status:503", "exception:MaintenanceWindow"), handled.toList())
    }

    @Test
    fun statusPageKeepsTheChallengeOfTheResponseItReplaces() = testApplication {
        val handled: MutableList<String> = orderDesk()

        val refused: HttpResponse = client.get("/ledger") { bearerAuth("forged-token") }
        val admitted: HttpResponse = client.get("/ledger") { bearerAuth("dana-token") }

        assertEquals(HttpStatusCode.Unauthorized, refused.status)
        assertEquals("sign in as a clerk to continue", refused.bodyAsText())
        assertEquals("Bearer realm=order-desk", refused.headers[HttpHeaders.WWWAuthenticate])
        assertEquals(HttpStatusCode.OK, admitted.status)
        assertEquals("ledger opened by dana", admitted.bodyAsText())
        assertEquals(listOf("status:401"), handled.toList())
    }

    private companion object {
        val ORDERS: Map<String, String> = mapOf("1001" to "2 x espresso beans")
        val CLERKS: Map<String, String> = mapOf("dana-token" to "dana")
    }
}
