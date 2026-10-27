/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_arrow_kt.arrow_fx_coroutines_jvm

import arrow.fx.coroutines.fixedRate
import arrow.fx.coroutines.mapIndexed
import arrow.fx.coroutines.metered
import arrow.fx.coroutines.parMap
import arrow.fx.coroutines.parMapNotNullUnordered
import arrow.fx.coroutines.parMapUnordered
import arrow.fx.coroutines.repeat
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.zip
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises Arrow's `Flow` extensions: order-preserving `parMap`, completion-ordered
 * `parMapUnordered` and `parMapNotNullUnordered`, and the stream shaping operators `mapIndexed`,
 * `repeat`, `metered` and `fixedRate`.
 *
 * A parcel tracker streams tracking numbers, looks each one up at a carrier concurrently, drops
 * unknown parcels, numbers the label batch it prints, and assigns pickups to couriers round-robin.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class, FlowPreview::class)
public class ShipmentTrackingFlowTest {

    /** The status a carrier reports for one parcel. */
    public data class TrackingStatus(val trackingNumber: String, val carrier: String, val state: String)

    /** A carrier directory that records how many lookups run at once and in which order they end. */
    public class CarrierDirectory(private val statuses: Map<String, TrackingStatus>) {

        private val inFlight: AtomicInteger = AtomicInteger()
        private val finishedGates: MutableMap<String, CompletableDeferred<Unit>> = ConcurrentHashMap()

        /** The highest number of lookups that ran at the same time. */
        public val peakConcurrency: AtomicInteger = AtomicInteger()

        /** Tracking numbers in the order their lookups finished. */
        public val completionOrder: MutableList<String> = Collections.synchronizedList(mutableListOf())

        /** Completes once the lookup of [trackingNumber] has finished. */
        public fun finished(trackingNumber: String): CompletableDeferred<Unit> =
            finishedGates.computeIfAbsent(trackingNumber) { CompletableDeferred() }

        /** Looks up [trackingNumber], first waiting for [after] when it is given. */
        public suspend fun lookup(trackingNumber: String, after: CompletableDeferred<Unit>? = null): TrackingStatus? {
            peakConcurrency.accumulateAndGet(inFlight.incrementAndGet(), ::maxOf)
            try {
                if (after != null) {
                    withTimeout(10.seconds) { after.await() }
                }
                return withContext(Dispatchers.IO) { statuses[trackingNumber] }
            } finally {
                inFlight.decrementAndGet()
                completionOrder.add(trackingNumber)
                finished(trackingNumber).complete(Unit)
            }
        }
    }

    private val directory: CarrierDirectory = CarrierDirectory(
        listOf(
            TrackingStatus("TRK-100", "Northwind", "IN_TRANSIT"),
            TrackingStatus("TRK-101", "Northwind", "DELIVERED"),
            TrackingStatus("TRK-102", "Swiftline", "OUT_FOR_DELIVERY"),
            TrackingStatus("TRK-103", "Swiftline", "IN_TRANSIT"),
            TrackingStatus("TRK-104", "Northwind", "LABEL_CREATED"),
            TrackingStatus("TRK-105", "Swiftline", "DELIVERED"),
        ).associateBy { it.trackingNumber },
    )

    @Test
    fun parMapKeepsUpstreamOrderEvenWhenLaterLookupsFinishFirst() {
        val trackingNumbers: Flow<String> =
            flowOf("TRK-100", "TRK-101", "TRK-102", "TRK-103", "TRK-104", "TRK-105")

        val states: List<String> = runBlocking {
            trackingNumbers
                .parMap(concurrency = 3) { trackingNumber ->
                    val after: CompletableDeferred<Unit>? =
                        if (trackingNumber == "TRK-100") directory.finished("TRK-102") else null
                    checkNotNull(directory.lookup(trackingNumber, after)).state
                }
                .toList()
        }

        assertEquals(
            listOf("IN_TRANSIT", "DELIVERED", "OUT_FOR_DELIVERY", "IN_TRANSIT", "LABEL_CREATED", "DELIVERED"),
            states,
        )
        val completions: List<String> = directory.completionOrder.toList()
        assertTrue(
            completions.indexOf("TRK-102") < completions.indexOf("TRK-100"),
            "lookups finished in the order $completions",
        )
        val peak: Int = directory.peakConcurrency.get()
        assertTrue(peak in 2..3, "parMap ran $peak lookups at once with a limit of 3")
    }

    @Test
    fun parMapUnorderedEmitsEachStatusAsSoonAsItsLookupFinishes() {
        val swiftlineSeen: CompletableDeferred<Unit> = CompletableDeferred()

        val emitted: List<String> = runBlocking {
            flowOf("TRK-101", "TRK-102")
                .parMapUnordered(concurrency = 2) { trackingNumber ->
                    val after: CompletableDeferred<Unit>? = if (trackingNumber == "TRK-101") swiftlineSeen else null
                    checkNotNull(directory.lookup(trackingNumber, after))
                }
                .onEach { status -> if (status.carrier == "Swiftline") swiftlineSeen.complete(Unit) }
                .map { status -> status.trackingNumber }
                .toList()
        }

        assertEquals(listOf("TRK-102", "TRK-101"), emitted)
    }

    @Test
    fun parMapNotNullUnorderedDropsParcelsNoCarrierKnows() {
        val scanned: List<String> = listOf("TRK-103", "TRK-999", "TRK-105", "TRK-404", "TRK-100")

        val known: List<TrackingStatus> = runBlocking {
            scanned.asFlow()
                .parMapNotNullUnordered(concurrency = 4) { trackingNumber -> directory.lookup(trackingNumber) }
                .toList()
        }

        assertEquals(
            setOf("TRK-103", "TRK-105", "TRK-100"),
            known.map { it.trackingNumber }.toSet(),
        )
        assertEquals(3, known.size)
        assertEquals(scanned.toSet(), directory.completionOrder.toSet())
    }

    @Test
    fun labelBatchIsNumberedAndPickupsRotateAcrossCouriers() {
        val parcels: List<String> = listOf("TRK-100", "TRK-101", "TRK-102", "TRK-103", "TRK-104")

        val labels: List<String> = runBlocking {
            parcels.asFlow()
                .mapIndexed { index, trackingNumber -> "#${index + 1} $trackingNumber" }
                .toList()
        }
        assertEquals(
            listOf("#1 TRK-100", "#2 TRK-101", "#3 TRK-102", "#4 TRK-103", "#5 TRK-104"),
            labels,
        )

        val assignments: List<Pair<String, String>> = runBlocking {
            parcels.asFlow()
                .zip(flowOf("Ana", "Ben").repeat()) { trackingNumber, courier -> trackingNumber to courier }
                .metered(1.milliseconds)
                .toList()
        }
        assertEquals(
            listOf(
                "TRK-100" to "Ana",
                "TRK-101" to "Ben",
                "TRK-102" to "Ana",
                "TRK-103" to "Ben",
                "TRK-104" to "Ana",
            ),
            assignments,
        )

        val polls: List<String> = runBlocking {
            fixedRate(1.milliseconds)
                .take(3)
                .mapIndexed { index, _ -> checkNotNull(directory.lookup(parcels[index])).state }
                .toList()
        }
        assertEquals(listOf("IN_TRANSIT", "DELIVERED", "OUT_FOR_DELIVERY"), polls)
    }
}
