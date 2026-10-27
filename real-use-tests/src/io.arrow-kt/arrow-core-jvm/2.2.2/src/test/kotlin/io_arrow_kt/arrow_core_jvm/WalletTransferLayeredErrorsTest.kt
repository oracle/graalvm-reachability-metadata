/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_arrow_kt.arrow_core_jvm

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.merge
import arrow.core.raise.ExperimentalTraceApi
import arrow.core.raise.Raise
import arrow.core.raise.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import arrow.core.raise.fold
import arrow.core.raise.recover
import arrow.core.raise.result
import arrow.core.raise.traced
import arrow.core.raise.withError
import arrow.core.recover
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Short-circuiting typed-error propagation across application layers with `either {}`,
 * `withError`, `catch`, `recover`, `result {}` and `traced`.
 *
 * A wallet service moves money between accounts. Each layer owns its error type: the request
 * parser raises `ParseError` (wrapping the `NumberFormatException` a bad amount throws with
 * `catch`), the ledger raises `LedgerError`, and the transfer service translates both into its
 * own `TransferError` with `withError` before it reaches the HTTP edge, which folds the outcome
 * into a status line. A transfer to a frozen account is recovered by parking the money in a
 * suspense account, `traced` records where a ledger error was raised for the audit log, and the
 * same ledger is driven through `Either` combinators and Kotlin's `Result` with `result {}`.
 */
class WalletTransferLayeredErrorsTest {

    data class TransferRequest(val from: String, val to: String, val amount: BigDecimal)

    data class Receipt(val from: String, val to: String, val amount: BigDecimal, val fromBalance: BigDecimal)

    sealed interface ParseError {
        data class MissingField(val name: String) : ParseError
        data class BadAmount(val raw: String) : ParseError
    }

    sealed interface LedgerError {
        data class UnknownAccount(val id: String) : LedgerError
        data class Frozen(val id: String) : LedgerError
        data class InsufficientFunds(val id: String, val shortBy: BigDecimal) : LedgerError
    }

    sealed interface TransferError {
        data class InvalidRequest(val reason: String) : TransferError
        data class Rejected(val reason: String) : TransferError
        data object SameAccount : TransferError
    }

    /** In-memory ledger whose operations raise `LedgerError` through the `Raise` receiver. */
    class Ledger(balances: Map<String, BigDecimal>, private val frozen: Set<String>) {
        private val balances: MutableMap<String, BigDecimal> = balances.toMutableMap()

        fun balance(id: String): BigDecimal = balances.getValue(id)

        fun Raise<LedgerError>.debit(id: String, amount: BigDecimal): BigDecimal {
            val current: BigDecimal = ensureNotNull(balances[id]) { LedgerError.UnknownAccount(id) }
            ensure(id !in frozen) { LedgerError.Frozen(id) }
            ensure(current >= amount) { LedgerError.InsufficientFunds(id, amount - current) }
            val updated: BigDecimal = current - amount
            balances[id] = updated
            return updated
        }

        fun Raise<LedgerError>.credit(id: String, amount: BigDecimal) {
            val current: BigDecimal = ensureNotNull(balances[id]) { LedgerError.UnknownAccount(id) }
            ensure(id !in frozen) { LedgerError.Frozen(id) }
            balances[id] = current + amount
        }
    }

    private fun ledger(): Ledger = Ledger(
        balances = mapOf(
            "alice" to BigDecimal("100.00"),
            "bob" to BigDecimal("20.00"),
            "carol" to BigDecimal("0.00"),
            "suspense" to BigDecimal("0.00"),
        ),
        frozen = setOf("carol"),
    )

    private fun Raise<ParseError>.parse(form: Map<String, String>): TransferRequest {
        val from: String = ensureNotNull(form["from"]) { ParseError.MissingField("from") }
        val to: String = ensureNotNull(form["to"]) { ParseError.MissingField("to") }
        val raw: String = ensureNotNull(form["amount"]) { ParseError.MissingField("amount") }
        val amount: BigDecimal = catch({ BigDecimal(raw) }) { _: NumberFormatException ->
            raise(ParseError.BadAmount(raw))
        }
        ensure(amount.signum() > 0) { ParseError.BadAmount(raw) }
        return TransferRequest(from, to, amount)
    }

    private fun LedgerError.describe(): String = when (this) {
        is LedgerError.UnknownAccount -> "unknown account $id"
        is LedgerError.Frozen -> "account $id is frozen"
        is LedgerError.InsufficientFunds -> "account $id is short by $shortBy"
    }

    /** The service layer: every lower-layer error is translated into a `TransferError`. */
    private fun transfer(ledger: Ledger, form: Map<String, String>): Either<TransferError, Receipt> = either {
        val request: TransferRequest = withError({ error: ParseError ->
            when (error) {
                is ParseError.MissingField -> TransferError.InvalidRequest("missing ${error.name}")
                is ParseError.BadAmount -> TransferError.InvalidRequest("bad amount '${error.raw}'")
            }
        }) { parse(form) }
        ensure(request.from != request.to) { TransferError.SameAccount }
        withError({ error: LedgerError -> TransferError.Rejected(error.describe()) }) {
            with(ledger) {
                val remaining: BigDecimal = debit(request.from, request.amount)
                credit(request.to, request.amount)
                Receipt(request.from, request.to, request.amount, remaining)
            }
        }
    }

    /** Like [transfer], but money for a frozen account is parked in the suspense account. */
    private fun transferOrPark(ledger: Ledger, request: TransferRequest): Either<LedgerError, String> = either {
        with(ledger) {
            debit(request.from, request.amount)
            recover({
                credit(request.to, request.amount)
                "credited ${request.to}"
            }) { error: LedgerError ->
                if (error !is LedgerError.Frozen) raise(error)
                credit("suspense", request.amount)
                "parked for ${error.id}"
            }
        }
    }

    /** The HTTP edge folds the service outcome into a status line. */
    private fun respond(ledger: Ledger, form: Map<String, String>): String = fold(
        { transfer(ledger, form).bind() },
        { error: TransferError ->
            when (error) {
                is TransferError.InvalidRequest -> "400 ${error.reason}"
                is TransferError.Rejected -> "409 ${error.reason}"
                TransferError.SameAccount -> "422 cannot transfer to the same account"
            }
        },
        { receipt: Receipt -> "200 moved ${receipt.amount} from ${receipt.from} to ${receipt.to}" },
    )

    @Test
    fun successfulTransferMovesMoneyBetweenAccounts() {
        val ledger: Ledger = ledger()
        val outcome: Either<TransferError, Receipt> =
            transfer(ledger, mapOf("from" to "alice", "to" to "bob", "amount" to "30.50"))

        assertEquals(
            Either.Right(Receipt("alice", "bob", BigDecimal("30.50"), BigDecimal("69.50"))),
            outcome,
        )
        assertEquals(BigDecimal("50.50"), ledger.balance("bob"))
    }

    @Test
    fun parseErrorsAreTranslatedIntoInvalidRequests() {
        val ledger: Ledger = ledger()

        assertEquals("400 missing to", respond(ledger, mapOf("from" to "alice", "amount" to "1")))
        assertEquals(
            "400 bad amount 'ten'",
            respond(ledger, mapOf("from" to "alice", "to" to "bob", "amount" to "ten")),
        )
        assertEquals(
            "400 bad amount '-5'",
            respond(ledger, mapOf("from" to "alice", "to" to "bob", "amount" to "-5")),
        )
        assertEquals(
            "422 cannot transfer to the same account",
            respond(ledger, mapOf("from" to "bob", "to" to "bob", "amount" to "1")),
        )
        assertEquals(BigDecimal("100.00"), ledger.balance("alice"))
    }

    @Test
    fun ledgerErrorsShortCircuitBeforeAnyCredit() {
        val ledger: Ledger = ledger()

        assertEquals(
            "409 account bob is short by 80.00",
            respond(ledger, mapOf("from" to "bob", "to" to "alice", "amount" to "100.00")),
        )
        assertEquals(
            "409 unknown account dave",
            respond(ledger, mapOf("from" to "alice", "to" to "dave", "amount" to "5")),
        )
        assertEquals(BigDecimal("20.00"), ledger.balance("bob"))
        assertEquals(
            "200 moved 5 from alice to bob",
            respond(ledger, mapOf("from" to "alice", "to" to "bob", "amount" to "5")),
        )
    }

    @Test
    fun frozenRecipientIsRecoveredIntoTheSuspenseAccount() {
        val ledger: Ledger = ledger()

        val parked: Either<LedgerError, String> =
            transferOrPark(ledger, TransferRequest("alice", "carol", BigDecimal("12.00")))
        val credited: Either<LedgerError, String> =
            transferOrPark(ledger, TransferRequest("alice", "bob", BigDecimal("8.00")))
        val unknown: Either<LedgerError, String> =
            transferOrPark(ledger, TransferRequest("alice", "zed", BigDecimal("1.00")))

        assertEquals(Either.Right("parked for carol"), parked)
        assertEquals(Either.Right("credited bob"), credited)
        assertEquals(Either.Left(LedgerError.UnknownAccount("zed")), unknown)
        assertEquals(BigDecimal("12.00"), ledger.balance("suspense"))
        assertEquals(BigDecimal("0.00"), ledger.balance("carol"))
    }

    @OptIn(ExperimentalTraceApi::class)
    @Test
    fun tracedRecordsWhereALedgerErrorWasRaised() {
        val ledger: Ledger = ledger()
        val auditLog: MutableList<String> = mutableListOf()

        val outcome: Either<LedgerError, BigDecimal> = either {
            traced({
                with(ledger) { debit("bob", BigDecimal("50.00")) }
            }) { trace, error: LedgerError ->
                val stack: String = trace.stackTraceToString()
                auditLog += "${error.describe()} (trace captured: ${stack.isNotBlank()})"
            }
        }

        assertEquals(Either.Left(LedgerError.InsufficientFunds("bob", BigDecimal("30.00"))), outcome)
        assertEquals(listOf("account bob is short by 30.00 (trace captured: true)"), auditLog)
    }

    @Test
    fun ledgerResultsComposeThroughEitherAndKotlinResult() {
        val ledger: Ledger = ledger()

        val chained: Either<LedgerError, BigDecimal> =
            either<LedgerError, BigDecimal> { with(ledger) { debit("alice", BigDecimal("10.00")) } }
                .flatMap { remaining ->
                    either { with(ledger) { debit("alice", remaining) } }
                }
        assertEquals(Either.Right(BigDecimal("0.00")), chained)

        val retried: Either<String, String> =
            either<LedgerError, String> { with(ledger) { credit("carol", BigDecimal.ONE) }; "ok" }
                .recover { error: LedgerError ->
                    if (error is LedgerError.Frozen) raise("retry later: ${error.id}") else "ignored"
                }
        assertEquals(Either.Left("retry later: carol"), retried)
        assertEquals("retry later: carol", retried.swap().merge())

        val parsedAmount: Result<BigDecimal> = result { BigDecimal("12.5") }
        val brokenAmount: Result<BigDecimal> = result {
            val fromCache: Result<BigDecimal> = runCatching { BigDecimal("1,5") }
            fromCache.bind()
        }
        assertEquals(BigDecimal("12.5"), parsedAmount.getOrThrow())
        assertTrue(brokenAmount.exceptionOrNull() is NumberFormatException)

        val description: String = Either.catch { BigDecimal("") }
            .mapLeft { "unparseable: ${it.javaClass.simpleName}" }
            .fold({ it }, { it.toPlainString() })
        assertEquals("unparseable: NumberFormatException", description)
        assertFalse(retried.isRight())
    }
}
