/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_coroutines_reactor

import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.reactor.flux
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import reactor.core.publisher.Flux
import reactor.test.StepVerifier

/**
 * Exercises the coroutine Flux builder for a live shipment-tracking event feed.
 * A reactive client receives warehouse scans and disconnecting releases its coroutine inbox.
 */
@Timeout(60)
class ShipmentTrackingFluxTest {
    @Test
    fun deliversTrackingUpdatesAndReleasesTheSessionOnDisconnect(): Unit {
        TrackingSession("SHIP-204").use { session ->
            StepVerifier.create(session.events(), 0)
                .then { session.record(WarehouseScan("ACCEPTED", "north-depot")) }
                .thenRequest(1)
                .expectNext("SHIP-204:ACCEPTED:north-depot")
                .then { session.record(WarehouseScan("DISPATCHED", "loading-bay-3")) }
                .thenRequest(1)
                .expectNext("SHIP-204:DISPATCHED:loading-bay-3")
                .thenCancel()
                .verify(Duration.ofSeconds(10))

            assertTrue(
                session.released.await(10, TimeUnit.SECONDS),
                "Disconnecting the tracking client must release its event inbox"
            )
        }
    }

    private class TrackingSession(private val shipmentId: String) : AutoCloseable {
        private val inbox: Channel<WarehouseScan> = Channel(Channel.UNLIMITED)
        val released: CountDownLatch = CountDownLatch(1)

        fun record(scan: WarehouseScan): Unit {
            check(inbox.trySend(scan).isSuccess) { "Tracking session is closed" }
        }

        fun events(): Flux<String> = flux {
            try {
                for (scan: WarehouseScan in inbox) {
                    send("$shipmentId:${scan.status}:${scan.location}")
                }
            } finally {
                inbox.cancel()
                released.countDown()
            }
        }

        override fun close(): Unit {
            inbox.close()
        }
    }

    private data class WarehouseScan(val status: String, val location: String)
}
