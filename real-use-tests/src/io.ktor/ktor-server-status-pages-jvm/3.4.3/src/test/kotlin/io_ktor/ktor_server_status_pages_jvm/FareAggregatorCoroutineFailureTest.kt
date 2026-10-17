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
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Exception handlers for failures raised in coroutines a route starts, where a
 * fare aggregator fans out to airlines and maps every failed lookup to an error.
 *
 * A failure on another dispatcher and a failed `async` child of the route's
 * `coroutineScope` reach the handler for their own type after the sibling
 * lookups are cancelled, and an expired `withTimeout` reaches the handler for
 * `TimeoutCancellationException`, while complete fan-outs pass through untouched.
 */
public class FareAggregatorCoroutineFailureTest {
    /** An airline whose fare service refused the lookup. */
    private class AirlineUnavailable(val airline: String) : RuntimeException("$airline is unavailable")

    /**
     * The airlines' fare services, recording which lookups were cancelled.
     *
     * With [skylineFailsAfterAeroParks], skyline refuses only once aero's lookup is parked, so
     * the failure cancels a running sibling rather than one that has not started yet.
     */
    private class FareDesk(private val skylineFailsAfterAeroParks: Boolean = false) {
        val cancelled: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()
        private val aeroParked: CompletableDeferred<Unit> = CompletableDeferred()

        /** The fare [airline] quotes for [route], looked up off the call's dispatcher. */
        suspend fun quote(airline: String, route: String): Int = withContext(Dispatchers.Default) {
            when (airline to route) {
                "skyline" to "BEG-LIS" -> {
                    if (skylineFailsAfterAeroParks) {
                        aeroParked.await()
                    }
                    throw AirlineUnavailable(airline)
                }
                "aero" to "BEG-LIS", "aero" to "BEG-OSL" -> parkUntilCancelled(airline)
                else -> FARES.getValue(airline) + route.length
            }
        }

        private suspend fun parkUntilCancelled(airline: String): Nothing {
            try {
                aeroParked.complete(Unit)
                awaitCancellation()
            } finally {
                cancelled.add(airline)
            }
        }
    }

    /** The aggregator application: error mapping and routes over [desk]. */
    private fun ApplicationTestBuilder.aggregator(desk: FareDesk) {
        application {
            install(StatusPages) {
                exception<AirlineUnavailable> { call, cause ->
                    call.respondText("${cause.airline} could not quote this route", status = HttpStatusCode.BadGateway)
                }
                exception<TimeoutCancellationException> { call, _ ->
                    call.respondText("airlines did not answer in time", status = HttpStatusCode.GatewayTimeout)
                }
            }
            routing {
                get("/fares/{route}") {
                    val route: String = call.parameters["route"]!!
                    val quotes: List<Int> = coroutineScope {
                        val lookups: List<Deferred<Int>> = FARES.keys.map { airline ->
                            async { desk.quote(airline, route) }
                        }
                        lookups.awaitAll()
                    }
                    call.respondText("$route from ${quotes.min()} EUR")
                }
                get("/fares/{route}/direct/{airline}") {
                    val route: String = call.parameters["route"]!!
                    val airline: String = call.parameters["airline"]!!
                    call.respondText("$airline: ${desk.quote(airline, route)} EUR")
                }
                get("/fares/{route}/express") {
                    val route: String = call.parameters["route"]!!
                    val fare: Int = withTimeout(EXPRESS_BUDGET_MILLIS) {
                        desk.quote("aero", route)
                    }
                    call.respondText("$route express at $fare EUR")
                }
            }
        }
    }

    @Test
    fun completeFanOutIsServedAsIs() = testApplication {
        val desk: FareDesk = FareDesk()
        aggregator(desk)

        val response: HttpResponse = client.get("/fares/BEG-VIE")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("BEG-VIE from 97 EUR", response.bodyAsText())
        assertEquals(emptyList<String>(), desk.cancelled.toList())
    }

    @Test
    fun failureOnAnotherDispatcherKeepsItsType() = testApplication {
        val desk: FareDesk = FareDesk()
        aggregator(desk)

        val failed: HttpResponse = client.get("/fares/BEG-LIS/direct/skyline")
        val served: HttpResponse = client.get("/fares/BEG-LIS/direct/budgetair")

        assertEquals(HttpStatusCode.BadGateway, failed.status)
        assertEquals("skyline could not quote this route", failed.bodyAsText())
        assertEquals(HttpStatusCode.OK, served.status)
        assertEquals("budgetair: 127 EUR", served.bodyAsText())
    }

    @Test
    fun failedAsyncChildCancelsItsSiblingsAndReachesItsHandler() = testApplication {
        val desk: FareDesk = FareDesk(skylineFailsAfterAeroParks = true)
        aggregator(desk)

        val response: HttpResponse = client.get("/fares/BEG-LIS")

        assertEquals(HttpStatusCode.BadGateway, response.status)
        assertEquals("skyline could not quote this route", response.bodyAsText())
        assertEquals(listOf("aero"), desk.cancelled.toList())
    }

    @Test
    fun expiredTimeoutReachesTheTimeoutHandler() = testApplication {
        val desk: FareDesk = FareDesk()
        aggregator(desk)

        val response: HttpResponse = client.get("/fares/BEG-OSL/express")

        assertEquals(HttpStatusCode.GatewayTimeout, response.status)
        assertEquals("airlines did not answer in time", response.bodyAsText())
        assertEquals(listOf("aero"), desk.cancelled.toList())
    }

    private companion object {
        const val EXPRESS_BUDGET_MILLIS: Long = 200
        val FARES: Map<String, Int> = mapOf("aero" to 140, "skyline" to 90, "budgetair" to 120)
    }
}
