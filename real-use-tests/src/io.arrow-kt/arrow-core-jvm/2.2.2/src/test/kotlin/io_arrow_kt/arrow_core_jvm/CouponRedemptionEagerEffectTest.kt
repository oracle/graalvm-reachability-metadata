/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_arrow_kt.arrow_core_jvm

import arrow.core.Ior
import arrow.core.None
import arrow.core.Some
import arrow.core.left
import arrow.core.raise.EagerEffect
import arrow.core.raise.catch
import arrow.core.raise.eagerEffect
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import arrow.core.raise.fold
import arrow.core.raise.get
import arrow.core.raise.getOrElse
import arrow.core.raise.getOrNull
import arrow.core.raise.mapError
import arrow.core.raise.merge
import arrow.core.raise.recover
import arrow.core.raise.toEither
import arrow.core.raise.toIor
import arrow.core.raise.toOption
import arrow.core.raise.toResult
import arrow.core.right
import java.time.LocalDate
import java.time.format.DateTimeParseException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Non-suspending `EagerEffect` computations built with `eagerEffect {}` and run synchronously.
 *
 * A point-of-sale terminal redeems coupon codes against a basket without any coroutine
 * machinery. Each rule is a cold `EagerEffect` that raises a typed `CouponError`: rules are
 * composed with `bind`, unknown codes fall back to the house coupon through `recover`, a legacy
 * ledger's parse failures become domain errors through `catch`, and the till reads the outcome
 * through `fold`, `toEither`, `toIor`, `toOption`, `toResult`, `getOrNull`, `getOrElse`,
 * `mapError`, `merge` and `get`.
 */
class CouponRedemptionEagerEffectTest {

    sealed interface CouponError {
        data class UnknownCode(val code: String) : CouponError

        data class Expired(val code: String, val expiredOn: LocalDate) : CouponError

        data class BelowMinimum(val code: String, val minimumCents: Long, val basketCents: Long) :
            CouponError

        data class LedgerUnreadable(val line: String) : CouponError
    }

    data class Coupon(val code: String, val percentOff: Int, val minimumCents: Long, val validUntil: LocalDate)

    data class Basket(val cents: Long, val today: LocalDate)

    data class Redemption(val code: String, val discountCents: Long, val payableCents: Long)

    /** The terminal's in-memory coupon book; `lookups` counts how often a rule actually ran. */
    class CouponBook(private val coupons: Map<String, Coupon>) {
        var lookups: Int = 0
            private set

        fun lookup(code: String): EagerEffect<CouponError, Coupon> = eagerEffect {
            lookups++
            ensureNotNull(coupons[code.uppercase()]) { CouponError.UnknownCode(code) }
        }

        fun validate(coupon: Coupon, basket: Basket): EagerEffect<CouponError, Coupon> = eagerEffect {
            ensure(!basket.today.isAfter(coupon.validUntil)) {
                CouponError.Expired(coupon.code, coupon.validUntil)
            }
            ensure(basket.cents >= coupon.minimumCents) {
                CouponError.BelowMinimum(coupon.code, coupon.minimumCents, basket.cents)
            }
            coupon
        }

        fun redeem(code: String, basket: Basket): EagerEffect<CouponError, Redemption> = eagerEffect {
            val coupon: Coupon = lookup(code).bind()
            validate(coupon, basket).bind()
            val discount: Long = basket.cents * coupon.percentOff / 100
            Redemption(coupon.code, discount, basket.cents - discount)
        }

        /** Falls back to the house coupon only when the code is unknown; other errors stand. */
        fun redeemOrHouse(code: String, basket: Basket): EagerEffect<CouponError, Redemption> =
            redeem(code, basket).recover { error: CouponError ->
                when (error) {
                    is CouponError.UnknownCode -> redeem(HOUSE_CODE, basket).bind()
                    else -> raise(error)
                }
            }
    }

    /** Reads `CODE;percent;minimumCents;validUntil` lines exported by the old till software. */
    fun importLedgerLine(line: String): EagerEffect<CouponError, Coupon> =
        eagerEffect<CouponError, Coupon> {
            val fields: List<String> = line.split(';')
            ensure(fields.size == 4) { CouponError.LedgerUnreadable(line) }
            Coupon(fields[0], fields[1].toInt(), fields[2].toLong(), LocalDate.parse(fields[3]))
        }.catch { _: NumberFormatException -> raise(CouponError.LedgerUnreadable(line)) }

    private val today: LocalDate = LocalDate.of(2026, 3, 14)

    private fun book(): CouponBook = CouponBook(
        mapOf(
            "SPRING20" to Coupon("SPRING20", 20, 5_000, LocalDate.of(2026, 3, 31)),
            "WINTER30" to Coupon("WINTER30", 30, 0, LocalDate.of(2026, 2, 28)),
            "BIGSPEND" to Coupon("BIGSPEND", 15, 20_000, LocalDate.of(2026, 12, 31)),
            HOUSE_CODE to Coupon(HOUSE_CODE, 5, 0, LocalDate.of(2099, 1, 1)),
        ),
    )

    @Test
    fun redemptionIsColdAndRerunsEveryTimeItIsInterpreted() {
        val coupons: CouponBook = book()
        val redemption: EagerEffect<CouponError, Redemption> = coupons.redeem("spring20", Basket(12_000, today))

        assertEquals(0, coupons.lookups, "building the effect must not run it")

        val expected: Redemption = Redemption("SPRING20", 2_400, 9_600)
        assertEquals(expected.right(), redemption.toEither())
        assertEquals(expected, redemption.getOrNull())
        assertEquals(Ior.Right(expected), redemption.toIor())
        assertEquals(3, coupons.lookups, "each interpretation runs the computation again")
    }

    @Test
    fun ruleViolationsShortCircuitIntoTypedErrors() {
        val coupons: CouponBook = book()

        val expired: EagerEffect<CouponError, Redemption> = coupons.redeem("WINTER30", Basket(8_000, today))
        assertEquals(CouponError.Expired("WINTER30", LocalDate.of(2026, 2, 28)).left(), expired.toEither())
        assertNull(expired.getOrNull())
        assertEquals(None, expired.toOption { None })

        val small: EagerEffect<CouponError, Redemption> = coupons.redeem("BIGSPEND", Basket(4_500, today))
        val receiptLine: String = small.fold(
            recover = { error: CouponError ->
                when (error) {
                    is CouponError.BelowMinimum ->
                        "${error.code}: spend ${error.minimumCents - error.basketCents} more cents"
                    else -> "rejected: $error"
                }
            },
            transform = { r: Redemption -> "${r.code}: -${r.discountCents}" },
        )
        assertEquals("BIGSPEND: spend 15500 more cents", receiptLine)
        assertEquals(Ior.Left(CouponError.BelowMinimum("BIGSPEND", 20_000, 4_500)), small.toIor())
    }

    @Test
    fun unknownCodesFallBackToTheHouseCouponButOtherErrorsStand() {
        val coupons: CouponBook = book()
        val basket: Basket = Basket(10_000, today)

        assertEquals(
            Redemption(HOUSE_CODE, 500, 9_500).right(),
            coupons.redeemOrHouse("TYPO15", basket).toEither(),
        )
        assertEquals(
            Some(Redemption("SPRING20", 2_000, 8_000)),
            coupons.redeemOrHouse("SPRING20", basket).toOption { None },
        )
        assertEquals(
            CouponError.Expired("WINTER30", LocalDate.of(2026, 2, 28)).left(),
            coupons.redeemOrHouse("WINTER30", basket).toEither(),
        )
    }

    @Test
    fun legacyLedgerParseFailuresBecomeDomainErrors() {
        assertEquals(
            Coupon("AUTUMN10", 10, 1_000, LocalDate.of(2026, 11, 30)).right(),
            importLedgerLine("AUTUMN10;10;1000;2026-11-30").toEither(),
        )
        assertEquals(
            CouponError.LedgerUnreadable("AUTUMN10;ten;1000;2026-11-30").left(),
            importLedgerLine("AUTUMN10;ten;1000;2026-11-30").toEither(),
        )
        assertEquals(
            CouponError.LedgerUnreadable("AUTUMN10;10").left(),
            importLedgerLine("AUTUMN10;10").toEither(),
        )

        // A malformed date is not a NumberFormatException, so `catch` lets it through to the
        // till, which turns any unexpected throwable into an operator message with `fold`.
        val badDate: EagerEffect<CouponError, Coupon> = importLedgerLine("AUTUMN10;10;1000;30.11.2026")
        val operatorMessage: String = badDate.fold(
            catch = { t: Throwable -> "ledger import crashed: ${t::class.simpleName}" },
            recover = { error: CouponError -> "ledger rejected: $error" },
            transform = { c: Coupon -> "imported ${c.code}" },
        )
        assertEquals("ledger import crashed: DateTimeParseException", operatorMessage)
        assertThrows(DateTimeParseException::class.java) { badDate.toEither() }
    }

    @Test
    fun tillAdaptersMapErrorsToStatusCodesAndResults() {
        val coupons: CouponBook = book()
        val basket: Basket = Basket(6_000, today)

        fun httpStatus(error: CouponError): Int = when (error) {
            is CouponError.UnknownCode -> 404
            is CouponError.Expired -> 410
            is CouponError.BelowMinimum -> 422
            is CouponError.LedgerUnreadable -> 500
        }

        assertEquals(410.left(), coupons.redeem("WINTER30", basket).mapError(::httpStatus).toEither())
        assertEquals(404.left(), coupons.redeem("NOPE", basket).mapError(::httpStatus).toEither())

        val asResult: Result<Redemption> = coupons.redeem("NOPE", basket).toResult { error: CouponError ->
            Result.failure(IllegalArgumentException("coupon refused: $error"))
        }
        assertTrue(asResult.isFailure)
        assertEquals("coupon refused: UnknownCode(code=NOPE)", asResult.exceptionOrNull()?.message)

        val throwing: EagerEffect<Throwable, Long> = eagerEffect { "12x".toLong() }
        assertTrue(throwing.recover { t: Throwable -> raise(IllegalStateException(t)) }.toResult().isFailure)
        assertEquals(Result.success(1_200L), eagerEffect<Throwable, Long> { 1_200L }.toResult())

        // Both branches render to the same type, so the till prints whichever one `merge` yields.
        val banner: EagerEffect<String, String> = coupons.redeem("BIGSPEND", basket)
            .mapError { error: CouponError -> "No discount ($error)" }
            .recover { message: String -> raise(message.substringBefore(" (")) }
            .let { effect: EagerEffect<String, Redemption> ->
                eagerEffect { "You saved ${effect.bind().discountCents} cents" }
            }
        assertEquals("No discount", banner.merge())

        // A computation that cannot fail reads its value with `get`; a failing one with `getOrElse`.
        val loyaltyPoints: EagerEffect<Nothing, Long> = eagerEffect { basket.cents / 100 }
        assertEquals(60L, loyaltyPoints.get())
        assertEquals(0L, coupons.redeem("NOPE", basket).getOrElse { _: CouponError -> null }?.discountCents ?: 0L)
    }

    companion object {
        const val HOUSE_CODE: String = "HOUSE5"
    }
}
