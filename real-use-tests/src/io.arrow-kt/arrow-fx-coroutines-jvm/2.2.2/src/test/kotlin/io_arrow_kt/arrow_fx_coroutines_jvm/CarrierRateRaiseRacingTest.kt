/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_arrow_kt.arrow_fx_coroutines_jvm

import arrow.core.Either
import arrow.core.raise.ensure
import arrow.fx.coroutines.ExitCase
import arrow.fx.coroutines.ExperimentalRacingApi
import arrow.fx.coroutines.RaiseScope
import arrow.fx.coroutines.guaranteeCase
import arrow.fx.coroutines.race
import arrow.fx.coroutines.raceOrFail
import arrow.fx.coroutines.racing
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises Arrow's `Raise`-aware racing: `race` and `raceOrFail` branches running in a
 * `RaiseScope` whose typed errors go to an error handler and drop the branch out of the race,
 * while thrown exceptions are absorbed by `race` and fail the whole `racing` block for `raceOrFail`.
 *
 * A checkout asks several shipping carriers for a rate at once and books the first usable quote; a
 * carrier that rejects the parcel raises a typed rejection that is logged against the carrier
 * named in the branch's coroutine context, and a stalled carrier is cancelled once a rival wins.
 */
@OptIn(ExperimentalRacingApi::class)
public class CarrierRateRaiseRacingTest {

    /** The parcel the checkout wants to ship. */
    public data class Parcel(val grams: Int, val destination: String)

    /** A bookable rate offered by one carrier. */
    public data class RateQuote(val carrier: String, val cents: Long)

    /** Why a carrier declined to quote. */
    public sealed interface CarrierRejection {

        public data class Overweight(val limitGrams: Int) : CarrierRejection

        public data class NoService(val destination: String) : CarrierRejection
    }

    /** A carrier's tariff: a per-kilogram price, a weight limit and the destinations it serves. */
    public class Carrier(
        public val name: String,
        private val centsPerKilo: Long,
        private val limitGrams: Int,
        private val destinations: Set<String>,
    ) {

        private fun coverage(destination: String): Either<CarrierRejection, Long> =
            if (destination in destinations) {
                Either.Right(if (destination == "IS") 400L else 0L)
            } else {
                Either.Left(CarrierRejection.NoService(destination))
            }

        /** Quotes [parcel], raising a rejection the race records instead of an exception. */
        public suspend fun RaiseScope<CarrierRejection>.quote(parcel: Parcel): RateQuote {
            ensure(parcel.grams <= limitGrams) { CarrierRejection.Overweight(limitGrams) }
            val remoteSurcharge = async { coverage(parcel.destination) }
            val surcharges: List<Long> = listOf(remoteSurcharge.await(), Either.Right(150L)).bindAll()
            val kilos: Long = (parcel.grams + 999L) / 1000L
            return RateQuote(name, kilos * centsPerKilo + surcharges.sum())
        }
    }

    /** Rejections reported by the race, keyed by the carrier named in the branch context. */
    public class RejectionLog {

        public val byCarrier: MutableMap<String, CarrierRejection> = ConcurrentHashMap()

        private val signals: Map<String, CompletableDeferred<Unit>> =
            listOf("nordpost", "skyfreight", "bayline").associateWith { CompletableDeferred() }

        public fun record(context: CoroutineContext, rejection: CarrierRejection) {
            val carrier: String = checkNotNull(context[CoroutineName]).name
            byCarrier[carrier] = rejection
            signals.getValue(carrier).complete(Unit)
        }

        /** Waits, bounded, until every named carrier has had its rejection recorded. */
        public suspend fun awaitRejections(vararg carriers: String) {
            withTimeout(10_000) { carriers.forEach { signals.getValue(it).await() } }
        }
    }

    /** A carrier whose API never answers; it records how it ended once the race cancels it. */
    public class StalledCarrier {

        public val started: CompletableDeferred<Unit> = CompletableDeferred()

        public val exit: CompletableDeferred<ExitCase> = CompletableDeferred()

        public suspend fun quote(): RateQuote =
            guaranteeCase({
                started.complete(Unit)
                awaitCancellation()
            }) { exitCase -> exit.complete(exitCase) }

        public suspend fun awaitExit(): ExitCase = withTimeout(10_000) { exit.await() }
    }

    private val nordpost: Carrier = Carrier("nordpost", 300, 2_000, setOf("DE", "IS"))
    private val skyfreight: Carrier = Carrier("skyfreight", 900, 30_000, setOf("US"))
    private val bayline: Carrier = Carrier("bayline", 500, 20_000, setOf("DE", "IS", "US"))

    private fun carrier(name: String): CoroutineContext = Dispatchers.IO + CoroutineName(name)

    @Test
    fun rejectingCarriersDropOutAndTheFirstUsableQuoteIsBooked() {
        val parcel = Parcel(grams = 4_200, destination = "IS")
        val log = RejectionLog()

        val booked: RateQuote = runBlocking {
            racing {
                race(log::record, carrier("nordpost")) { with(nordpost) { quote(parcel) } }
                race(log::record, carrier("skyfreight")) { with(skyfreight) { quote(parcel) } }
                race(log::record, carrier("bayline")) {
                    log.awaitRejections("nordpost", "skyfreight")
                    with(bayline) { quote(parcel) }
                }
            }
        }

        assertEquals(RateQuote("bayline", 5 * 500L + 400L + 150L), booked)
        assertEquals(
            mapOf(
                "nordpost" to CarrierRejection.Overweight(2_000),
                "skyfreight" to CarrierRejection.NoService("IS"),
            ),
            log.byCarrier.toMap(),
        )
    }

    @Test
    fun raceAbsorbsACarrierOutageAndCancelsTheStalledCarrier() {
        val parcel = Parcel(grams = 1_500, destination = "DE")
        val log = RejectionLog()
        val stalled = StalledCarrier()
        val outage: CompletableDeferred<Throwable> = CompletableDeferred()
        val outageHandler = CoroutineExceptionHandler { _, error -> outage.complete(error) }

        val booked: RateQuote = runBlocking {
            racing {
                race(log::record, carrier("skyfreight") + outageHandler) {
                    throw IOException("skyfreight rate API unavailable")
                }
                race(log::record, carrier("bayline")) { stalled.quote() }
                race(log::record, carrier("nordpost")) {
                    withTimeout(10_000) { outage.await() }
                    stalled.started.await()
                    with(nordpost) { quote(parcel) }
                }
            }
        }

        assertEquals(RateQuote("nordpost", 2 * 300L + 150L), booked)
        val absorbed: Throwable = runBlocking { withTimeout(10_000) { outage.await() } }
        assertTrue(absorbed is IOException, "outage surfaced as $absorbed")
        assertEquals("skyfreight rate API unavailable", absorbed.message)
        val stalledExit: ExitCase = runBlocking { stalled.awaitExit() }
        assertTrue(stalledExit is ExitCase.Cancelled, "stalled carrier ended with $stalledExit")
        assertTrue(log.byCarrier.isEmpty(), "unexpected rejections ${log.byCarrier}")
    }

    @Test
    fun raceOrFailToleratesRejectionsButAbortsCheckoutOnAnOutage() {
        val parcel = Parcel(grams = 25_000, destination = "DE")
        val log = RejectionLog()
        val stalled = StalledCarrier()

        val outcome: Result<RateQuote> = runCatching {
            runBlocking {
                racing {
                    raceOrFail(log::record, carrier("nordpost")) { with(nordpost) { quote(parcel) } }
                    raceOrFail(log::record, carrier("bayline")) { stalled.quote() }
                    raceOrFail(log::record, carrier("skyfreight")) {
                        log.awaitRejections("nordpost")
                        stalled.started.await()
                        throw IOException("skyfreight rate API unavailable")
                    }
                }
            }
        }

        val failure: Throwable? = outcome.exceptionOrNull()
        assertTrue(failure is IOException, "checkout ended with $outcome")
        assertEquals("skyfreight rate API unavailable", failure?.message)
        assertEquals(mapOf("nordpost" to CarrierRejection.Overweight(2_000)), log.byCarrier.toMap())
        val stalledExit: ExitCase = runBlocking { stalled.awaitExit() }
        assertTrue(stalledExit is ExitCase.Cancelled, "stalled carrier ended with $stalledExit")
    }
}
