/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_arrow_kt.arrow_core_jvm

import arrow.core.Either
import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import arrow.core.left
import arrow.core.raise.Effect
import arrow.core.raise.catch
import arrow.core.raise.effect
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import arrow.core.raise.fold
import arrow.core.raise.getOrElse
import arrow.core.raise.getOrNull
import arrow.core.raise.mapError
import arrow.core.raise.recover
import arrow.core.raise.toEither
import arrow.core.raise.toOption
import arrow.core.right
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Suspendable `Effect` computations built with `effect {}` and run inside kotlinx-coroutines.
 *
 * A shipping service prices parcels by asking several carrier gateways for a quote. Every
 * gateway call suspends on another dispatcher, so each pricing step is a cold `Effect` that
 * raises typed `QuoteError`s instead of throwing. The steps are composed with `bind`, carrier
 * I/O failures are turned into domain errors with `catch`, a backup carrier takes over through
 * `recover`, several carriers are asked concurrently with `async`, and the results reach the
 * HTTP layer through `fold`, `toEither`, `toOption`, `getOrElse` and `mapError`.
 */
class ShipmentQuoteEffectPipelineTest {

    sealed interface QuoteError {
        data class UnknownDestination(val code: String) : QuoteError

        data class Overweight(val carrier: String, val grams: Int, val limit: Int) : QuoteError

        data class CarrierUnavailable(val carrier: String, val reason: String) : QuoteError

        data object NoCarrierAvailable : QuoteError
    }

    data class Parcel(val destination: String, val grams: Int)

    data class Quote(val carrier: String, val zone: Int, val priceCents: Long)

    /** A remote carrier tariff service; `failure` simulates what its client throws. */
    class CarrierGateway(
        val name: String,
        val maxGrams: Int,
        private val centsPerKiloAndZone: Long,
        private val failure: Throwable? = null,
    ) {
        val calls: AtomicInteger = AtomicInteger()

        suspend fun price(zone: Int, grams: Int): Long = withContext(Dispatchers.Default) {
            calls.incrementAndGet()
            yield()
            if (failure != null) {
                throw failure
            }
            val kilos: Long = (grams + 999L) / 1000L
            centsPerKiloAndZone * zone * kilos
        }
    }

    private val zones: Map<String, Int> = mapOf("DE" to 1, "FR" to 2, "US" to 5)

    private val fastPost: CarrierGateway = CarrierGateway("FastPost", 2_000, 300)
    private val cargoLine: CarrierGateway = CarrierGateway("CargoLine", 30_000, 450)
    private val flakyAir: CarrierGateway =
        CarrierGateway("FlakyAir", 5_000, 200, IOException("read timed out"))

    private fun zoneOf(code: String): Effect<QuoteError, Int> = effect {
        ensureNotNull(zones[code]) { QuoteError.UnknownDestination(code) }
    }

    private fun rawQuote(gateway: CarrierGateway, parcel: Parcel): Effect<QuoteError, Quote> =
        effect {
            val zone: Int = zoneOf(parcel.destination).bind()
            ensure(parcel.grams <= gateway.maxGrams) {
                QuoteError.Overweight(gateway.name, parcel.grams, gateway.maxGrams)
            }
            Quote(gateway.name, zone, gateway.price(zone, parcel.grams))
        }

    /** Only I/O failures become a domain error; anything else is a bug and keeps propagating. */
    private fun quote(gateway: CarrierGateway, parcel: Parcel): Effect<QuoteError, Quote> =
        rawQuote(gateway, parcel).catch { e: IOException ->
            raise(QuoteError.CarrierUnavailable(gateway.name, e.message.orEmpty()))
        }

    private fun quoteWithBackup(
        primary: CarrierGateway,
        backup: CarrierGateway,
        parcel: Parcel,
    ): Effect<QuoteError, Quote> =
        quote(primary, parcel).recover { error: QuoteError ->
            when (error) {
                is QuoteError.CarrierUnavailable -> quote(backup, parcel).bind()
                else -> raise(error)
            }
        }

    private fun cheapest(gateways: List<CarrierGateway>, parcel: Parcel): Effect<QuoteError, Quote> =
        effect {
            zoneOf(parcel.destination).bind()
            val outcomes: List<Either<QuoteError, Quote>> = coroutineScope {
                gateways.map { gateway -> async { quote(gateway, parcel).toEither() } }.awaitAll()
            }
            val quotes: List<Quote> = outcomes.mapNotNull { it.getOrNull() }
            ensure(quotes.isNotEmpty()) { QuoteError.NoCarrierAvailable }
            quotes.minBy { it.priceCents }
        }

    private fun describe(error: QuoteError): String = when (error) {
        is QuoteError.UnknownDestination -> "We do not ship to ${error.code}"
        is QuoteError.Overweight -> "${error.carrier} accepts at most ${error.limit} g"
        is QuoteError.CarrierUnavailable -> "${error.carrier} is unavailable: ${error.reason}"
        QuoteError.NoCarrierAvailable -> "No carrier can take this parcel"
    }

    @Test
    fun effectsAreColdAndRunAgainOnEveryExecution() = runBlocking {
        val parcel: Parcel = Parcel("FR", 1_200)
        val pending: Effect<QuoteError, Quote> = quote(cargoLine, parcel)
        assertEquals(0, cargoLine.calls.get())

        val first: Either<QuoteError, Quote> = pending.toEither()
        val second: Either<QuoteError, Quote> = pending.toEither()

        assertEquals(Quote("CargoLine", 2, 1_800).right(), first)
        assertEquals(first, second)
        assertEquals(2, cargoLine.calls.get())
    }

    @Test
    fun raisedErrorsShortCircuitBeforeTheCarrierIsCalled() = runBlocking {
        val unknown: Either<QuoteError, Quote> = quote(fastPost, Parcel("JP", 500)).toEither()
        val heavy: Either<QuoteError, Quote> = quote(fastPost, Parcel("DE", 2_500)).toEither()

        assertEquals(QuoteError.UnknownDestination("JP").left(), unknown)
        assertEquals(QuoteError.Overweight("FastPost", 2_500, 2_000).left(), heavy)
        assertEquals(0, fastPost.calls.get())
    }

    @Test
    fun carrierIoFailuresBecomeDomainErrorsAndFallBackToTheBackup() = runBlocking {
        val parcel: Parcel = Parcel("FR", 1_000)

        val direct: Either<QuoteError, Quote> = quote(flakyAir, parcel).toEither()
        val withBackup: Either<QuoteError, Quote> =
            quoteWithBackup(flakyAir, fastPost, parcel).toEither()
        val tooHeavyForPrimary: Either<QuoteError, Quote> =
            quoteWithBackup(flakyAir, fastPost, Parcel("FR", 8_000)).toEither()

        assertEquals(QuoteError.CarrierUnavailable("FlakyAir", "read timed out").left(), direct)
        assertEquals(Quote("FastPost", 2, 600).right(), withBackup)
        assertEquals(QuoteError.Overweight("FlakyAir", 8_000, 5_000).left(), tooHeavyForPrimary)
        assertEquals(1, fastPost.calls.get())
    }

    @Test
    fun concurrentCarrierQuotesPickTheCheapestOffer() = runBlocking {
        val carriers: List<CarrierGateway> = listOf(fastPost, cargoLine, flakyAir)

        val light: Option<Quote> = cheapest(carriers, Parcel("DE", 1_500)).toOption { None }
        val heavy: Quote? = cheapest(carriers, Parcel("US", 12_000)).getOrNull()
        val nobody: Quote? = cheapest(listOf(flakyAir), Parcel("DE", 500)).getOrNull()

        assertEquals(Some(Quote("FastPost", 1, 600)), light)
        assertEquals(Quote("CargoLine", 5, 27_000), heavy)
        assertNull(nobody)
        assertEquals(1, fastPost.calls.get())
        assertEquals(2, cargoLine.calls.get())
        assertEquals(2, flakyAir.calls.get())
    }

    @Test
    fun errorsAreTranslatedIntoUserFacingMessages() = runBlocking {
        val carriers: List<CarrierGateway> = listOf(fastPost, flakyAir)

        val unknown: String = cheapest(carriers, Parcel("JP", 400))
            .mapError { error: QuoteError -> describe(error) }
            .fold({ message: String -> "400 $message" }, { quote: Quote -> "200 ${quote.carrier}" })
        val noCarrier: String = cheapest(carriers, Parcel("US", 9_000))
            .mapError { error: QuoteError -> describe(error) }
            .fold({ message: String -> "400 $message" }, { quote: Quote -> "200 ${quote.carrier}" })
        val counterPickup: Quote = Quote("Counter pickup", 0, 0)
        val fallback: Quote = cheapest(carriers, Parcel("US", 9_000))
            .getOrElse { _: QuoteError -> counterPickup }
        val ok: String = cheapest(carriers, Parcel("US", 900))
            .mapError { error: QuoteError -> describe(error) }
            .fold({ message: String -> "400 $message" }, { quote: Quote -> "200 ${quote.carrier}" })

        assertEquals("400 We do not ship to JP", unknown)
        assertEquals("400 No carrier can take this parcel", noCarrier)
        assertEquals(counterPickup, fallback)
        assertEquals("200 FastPost", ok)
    }

    @Test
    fun unexpectedFailuresReachTheThrowableHandlerOfFold() = runBlocking {
        val broken: CarrierGateway =
            CarrierGateway("Broken", 10_000, 100, IllegalStateException("tariff table corrupt"))

        val response: String = quote(broken, Parcel("DE", 700)).fold(
            { failure: Throwable -> "500 ${failure::class.simpleName}: ${failure.message}" },
            { error: QuoteError -> "400 ${describe(error)}" },
            { quote: Quote -> "200 ${quote.priceCents}" },
        )

        assertEquals("500 IllegalStateException: tariff table corrupt", response)
        assertEquals(1, broken.calls.get())
    }
}
