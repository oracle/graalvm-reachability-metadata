/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_arrow_kt.arrow_fx_coroutines_jvm

import arrow.fx.coroutines.CyclicBarrier
import arrow.fx.coroutines.parMapNotNull
import arrow.resilience.Schedule
import arrow.resilience.retry
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Exercises Arrow's null-filtering parallel traversal: `Iterable.parMapNotNull` resolving a batch
 * concurrently on a chosen dispatcher with an optional concurrency limit, keeping the input order
 * while dropping elements that have no result, and cancelling every sibling when one branch fails.
 *
 * A delivery planner geocodes the stops of a route against a postcode gazetteer held in an
 * in-memory H2 database behind a HikariCP pool. Stops whose postcode is unknown are left off the
 * route, and each lookup retries a throttled gateway with an arrow-resilience `Schedule`.
 */
public class DeliveryGeocodingParMapNotNullTest {

    /** A delivery stop as the order system hands it over. */
    public data class Stop(val orderId: String, val postcode: String)

    /** A stop placed on the map. */
    public data class RoutePoint(val orderId: String, val latitude: Double, val longitude: Double)

    /** The upstream geocoder refused a request for now; asking again later may succeed. */
    public class GatewayThrottledException(postcode: String) : RuntimeException("throttled on $postcode")

    /** Looks postcodes up in the gazetteer, refusing some requests the way a rate limiter does. */
    public class GeocodingGateway(private val pool: HikariDataSource) {

        private val refusalsLeft: MutableMap<String, AtomicInteger> = ConcurrentHashMap()

        /** Every request the gateway received, per postcode. */
        public val requests: MutableMap<String, AtomicInteger> = ConcurrentHashMap()

        /** The names of the threads the lookups ran on. */
        public val threads: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

        /** Makes the next [times] requests for [postcode] fail with [GatewayThrottledException]. */
        public fun throttle(postcode: String, times: Int) {
            refusalsLeft[postcode] = AtomicInteger(times)
        }

        /** The coordinates of [postcode], or `null` when the gazetteer does not know it. */
        public fun coordinates(postcode: String): Pair<Double, Double>? {
            requests.computeIfAbsent(postcode) { AtomicInteger() }.incrementAndGet()
            threads += Thread.currentThread().name
            val refusals: AtomicInteger? = refusalsLeft[postcode]
            if (refusals != null && refusals.getAndDecrement() > 0) {
                throw GatewayThrottledException(postcode)
            }
            return pool.connection.use { connection ->
                connection.prepareStatement("SELECT latitude, longitude FROM gazetteer WHERE postcode = ?").use {
                    it.setString(1, postcode)
                    it.executeQuery().use { rows -> if (rows.next()) rows.getDouble(1) to rows.getDouble(2) else null }
                }
            }
        }
    }

    /** Turns a list of stops into the points of a route. */
    public class RoutePlanner(
        private val gateway: GeocodingGateway,
        private val context: CoroutineContext,
        private val retries: Long,
        private val beforeLookup: suspend (Stop) -> Unit = {},
    ) {

        /** Geocodes [stops] in parallel, at most [concurrency] at once, skipping unknown postcodes. */
        public suspend fun plan(stops: List<Stop>, concurrency: Int = Int.MAX_VALUE): List<RoutePoint> =
            stops.parMapNotNull(context, concurrency) { stop ->
                beforeLookup(stop)
                Schedule.recurs<GatewayThrottledException>(retries).retry {
                    gateway.coordinates(stop.postcode)
                }?.let { (latitude, longitude) -> RoutePoint(stop.orderId, latitude, longitude) }
            }
    }

    private lateinit var pool: HikariDataSource

    @BeforeEach
    fun openGazetteer() {
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:gazetteer;DB_CLOSE_DELAY=-1"
            username = "sa"
            password = ""
            maximumPoolSize = 6
            poolName = "gazetteer"
        }
        pool = HikariDataSource(config)
        pool.connection.use { connection ->
            connection.createStatement().use {
                it.execute(
                    """
                    CREATE TABLE gazetteer(postcode VARCHAR(8) PRIMARY KEY, latitude DOUBLE, longitude DOUBLE);
                    INSERT INTO gazetteer VALUES ('10115', 52.532, 13.384), ('20095', 53.551, 10.001),
                        ('50667', 50.938, 6.957), ('60311', 50.110, 8.682), ('80331', 48.137, 11.575),
                        ('01067', 51.050, 13.737);
                    """.trimIndent(),
                )
            }
        }
    }

    @AfterEach
    fun closeGazetteer() {
        pool.connection.use { connection -> connection.createStatement().use { it.execute("DROP ALL OBJECTS") } }
        pool.close()
    }

    @Test
    fun routeKeepsStopOrderAndLeavesOutUnknownPostcodesEvenWhenLookupsFinishBackwards() {
        val stops: List<Stop> = listOf(
            Stop("A-1", "10115"),
            Stop("A-2", "99999"),
            Stop("A-3", "20095"),
            Stop("A-4", "50667"),
            Stop("A-5", "00000"),
            Stop("A-6", "60311"),
        )
        // Each lookup waits for the one after it, so the batch completes from the last stop back.
        val finished: Map<String, CompletableDeferred<Unit>> = stops.associate { it.orderId to CompletableDeferred() }
        val completionOrder: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val gateway = GeocodingGateway(pool)
        val planner = RoutePlanner(gateway, Dispatchers.IO, retries = 0) { stop ->
            val next: Stop? = stops.getOrNull(stops.indexOf(stop) + 1)
            if (next != null) finished.getValue(next.orderId).await()
            completionOrder += stop.orderId
            finished.getValue(stop.orderId).complete(Unit)
        }

        val route: List<RoutePoint> = runBlocking { withTimeout(10_000) { planner.plan(stops) } }

        assertEquals(listOf("A-6", "A-5", "A-4", "A-3", "A-2", "A-1"), completionOrder)
        assertEquals(
            listOf(
                RoutePoint("A-1", 52.532, 13.384),
                RoutePoint("A-3", 53.551, 10.001),
                RoutePoint("A-4", 50.938, 6.957),
                RoutePoint("A-6", 50.110, 8.682),
            ),
            route,
        )
        assertEquals(setOf("10115", "99999", "20095", "50667", "00000", "60311"), gateway.requests.keys)
    }

    @Test
    fun lookupsRunOnTheGeocoderPoolNoMoreThanTheConcurrencyLimitAtOnce() {
        val threadNumber = AtomicInteger()
        val executor: ExecutorService = Executors.newFixedThreadPool(4) { task ->
            Thread(task, "geocoder-${threadNumber.incrementAndGet()}").apply { isDaemon = true }
        }
        val dispatcher: ExecutorCoroutineDispatcher = executor.asCoroutineDispatcher()
        try {
            val inFlight = AtomicInteger()
            val peak = AtomicInteger()
            // Stops pair up at the barrier, so a limit of 2 is reached and a limit of 1 would hang.
            val pairUp = CyclicBarrier(2)
            val gateway = GeocodingGateway(pool)
            val planner = RoutePlanner(gateway, dispatcher, retries = 0) {
                peak.accumulateAndGet(inFlight.incrementAndGet(), ::maxOf)
                pairUp.await()
                inFlight.decrementAndGet()
            }
            val stops: List<Stop> = listOf("10115", "20095", "50667", "60311", "80331", "01067")
                .mapIndexed { index, postcode -> Stop("B-${index + 1}", postcode) }

            val route: List<RoutePoint> = runBlocking { withTimeout(10_000) { planner.plan(stops, concurrency = 2) } }

            assertEquals(stops.map { it.orderId }, route.map { it.orderId })
            assertEquals(2, peak.get(), "the batch ran ${peak.get()} lookups at once with a limit of 2")
            assertTrue(gateway.threads.isNotEmpty())
            assertTrue(gateway.threads.all { it.startsWith("geocoder-") }, "lookups ran on ${gateway.threads}")
        } finally {
            dispatcher.close()
        }
    }

    @Test
    fun throttledLookupsAreRetriedOnTheirOwnWithoutHoldingBackTheRestOfTheRoute() {
        val gateway = GeocodingGateway(pool)
        gateway.throttle("20095", times = 2)
        gateway.throttle("80331", times = 1)
        val planner = RoutePlanner(gateway, Dispatchers.IO, retries = 3)
        val stops: List<Stop> = listOf(
            Stop("C-1", "10115"),
            Stop("C-2", "20095"),
            Stop("C-3", "77777"),
            Stop("C-4", "80331"),
        )

        val route: List<RoutePoint> = runBlocking { withTimeout(10_000) { planner.plan(stops, concurrency = 3) } }

        assertEquals(
            listOf(
                RoutePoint("C-1", 52.532, 13.384),
                RoutePoint("C-2", 53.551, 10.001),
                RoutePoint("C-4", 48.137, 11.575),
            ),
            route,
        )
        assertEquals(
            mapOf("10115" to 1, "20095" to 3, "77777" to 1, "80331" to 2),
            gateway.requests.mapValues { it.value.get() },
        )
    }

    @Test
    fun aStopThatStaysThrottledFailsTheRouteAndCancelsTheLookupsStillWaiting() {
        val gateway = GeocodingGateway(pool)
        gateway.throttle("50667", times = 10)
        val stops: List<Stop> = listOf(
            Stop("D-1", "10115"),
            Stop("D-2", "20095"),
            Stop("D-3", "50667"),
            Stop("D-4", "60311"),
        )
        val waiting: List<Stop> = stops.filter { it.postcode != "50667" }
        val started: Map<String, CompletableDeferred<Unit>> = waiting.associate { it.orderId to CompletableDeferred() }
        val upstreamNeverAnswers = CompletableDeferred<Unit>()
        val cancelled: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
        val planner = RoutePlanner(gateway, Dispatchers.IO, retries = 2) { stop ->
            if (stop.postcode == "50667") {
                // Fail only once every other lookup is in flight, so all of them must be cancelled.
                started.values.awaitAll()
            } else {
                started.getValue(stop.orderId).complete(Unit)
                try {
                    upstreamNeverAnswers.await()
                } catch (cancellation: CancellationException) {
                    cancelled += stop.orderId
                    throw cancellation
                }
            }
        }

        val failure: GatewayThrottledException = assertThrows(GatewayThrottledException::class.java) {
            runBlocking { withTimeout(10_000) { planner.plan(stops) } }
        }

        assertEquals("throttled on 50667", failure.message)
        assertEquals(3, gateway.requests.getValue("50667").get(), "one request plus two retries")
        assertEquals(setOf("D-1", "D-2", "D-4"), cancelled)
        assertEquals(setOf("50667"), gateway.requests.keys)
    }
}
