/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_arrow_kt.arrow_core_jvm

import arrow.core.Either
import arrow.core.EitherNel
import arrow.core.Ior
import arrow.core.NonEmptyList
import arrow.core.combine
import arrow.core.compareTo
import arrow.core.flattenOrAccumulate
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.leftNel
import arrow.core.nonEmptyListOf
import arrow.core.right
import arrow.core.separateIor
import arrow.core.toEitherNel
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Value-level `Either` combinators used without the Raise DSL (`Either.catchOrThrow`, the static
 * `Either.zipOrAccumulate` overloads, `leftNel`, `toEitherNel`, the `flattenOrAccumulate` overloads,
 * `combine`, `compareTo`, `onLeft`/`onRight`, `swap`, `isLeft`/`isRight`
 * predicates and `toIor` with `separateIor`).
 *
 * A bookkeeping service imports semicolon-separated bank statements. Every field parser returns an
 * `Either`, a line is assembled with `Either.zipOrAccumulate` so that one pass reports every bad
 * field, and the lines of a statement are folded into one result with `flattenOrAccumulate`.
 * The statement header carries its own string-joined errors, branch totals are merged with
 * `combine`, and the import outcomes are ranked and split into errors and transactions.
 */
class BankStatementImportEitherCombinatorsTest {

    /** A field the importer could not accept, tagged with its 1-based statement line. */
    sealed interface ImportError {
        val line: Int

        data class MalformedLine(override val line: Int, val fieldCount: Int) : ImportError

        data class BadDate(override val line: Int, val raw: String) : ImportError

        data class BadAmount(override val line: Int, val raw: String) : ImportError

        data class MissingCounterparty(override val line: Int) : ImportError

        data class BadHeader(override val line: Int, val reason: String) : ImportError
    }

    /** One booked movement on the account, in cents. */
    data class Transaction(val date: LocalDate, val amountCents: Long, val counterparty: String)

    /** The account a statement belongs to. */
    data class Header(val iban: String, val currency: String)

    /** A fully imported statement. */
    data class Statement(val header: Header, val transactions: List<Transaction>) {
        val balanceCents: Long
            get() = transactions.sumOf { it.amountCents }
    }

    /** Parses the field-level pieces of a statement into `Either` values. */
    object StatementParser {
        private val supportedCurrencies: Set<String> = setOf("EUR", "CHF", "USD")

        fun date(line: Int, raw: String): Either<ImportError, LocalDate> =
            Either.catchOrThrow<DateTimeParseException, LocalDate> { LocalDate.parse(raw) }
                .mapLeft { ImportError.BadDate(line, raw) }

        /** Sub-cent amounts are a bug in the bank export, so their `ArithmeticException` escapes. */
        fun amount(line: Int, raw: String): Either<ImportError, Long> =
            Either.catchOrThrow<NumberFormatException, Long> {
                BigDecimal(raw).movePointRight(2).longValueExact()
            }.mapLeft { ImportError.BadAmount(line, raw) }

        fun counterparty(line: Int, raw: String): Either<ImportError, String> =
            if (raw.isBlank()) ImportError.MissingCounterparty(line).left() else raw.trim().right()

        fun line(line: Int, text: String): EitherNel<ImportError, Transaction> {
            val fields: List<String> = text.split(';')
            if (fields.size != 3) {
                return ImportError.MalformedLine(line, fields.size).leftNel()
            }
            return Either.zipOrAccumulate(
                date(line, fields[0]),
                amount(line, fields[1]),
                counterparty(line, fields[2]),
                ::Transaction,
            )
        }

        /** Header problems are shown to the operator as one sentence. */
        fun headerProblems(text: String): Either<String, Header> {
            val fields: List<String> = text.split(';')
            val iban: Either<String, String> =
                fields.getOrNull(0)?.takeIf { it.matches(Regex("[A-Z]{2}[0-9]{2}[A-Z0-9]{8,30}")) }
                    ?.right() ?: "IBAN '${fields.getOrNull(0).orEmpty()}' is invalid".left()
            val currency: Either<String, String> =
                fields.getOrNull(1)?.takeIf { it in supportedCurrencies }?.right()
                    ?: "currency '${fields.getOrNull(1).orEmpty()}' is unsupported".left()
            return Either.zipOrAccumulate({ first: String, second: String -> "$first and $second" },
                iban, currency, ::Header)
        }

        fun statement(text: String): EitherNel<ImportError, Statement> {
            val lines: List<String> = text.trimIndent().lines()
            val header: EitherNel<ImportError, Header> =
                headerProblems(lines.first()).mapLeft { ImportError.BadHeader(1, it) }.toEitherNel()
            val transactions: EitherNel<ImportError, List<Transaction>> = lines.drop(1)
                .mapIndexed { index: Int, text: String -> line(index + 2, text) }
                .flattenOrAccumulate()
            return Either.zipOrAccumulate(header, transactions, ::Statement)
        }
    }

    @Test
    fun cleanStatementImportsEveryTransaction() {
        val audit: MutableList<String> = mutableListOf()
        val statement: Statement = StatementParser.statement(
            """
            DE89370400440532013000;EUR
            2026-03-01;1250.00;ACME Payroll
            2026-03-03;-42.50; Corner Bakery
            2026-03-04;-7;Transit Pass
            """,
        ).onRight { audit += "imported ${it.transactions.size} lines" }
            .onLeft { audit += "rejected" }
            .getOrElse { errors: NonEmptyList<ImportError> -> error("unexpected errors $errors") }

        assertEquals(Header("DE89370400440532013000", "EUR"), statement.header)
        assertEquals(
            listOf(
                Transaction(LocalDate.of(2026, 3, 1), 125_000, "ACME Payroll"),
                Transaction(LocalDate.of(2026, 3, 3), -4_250, "Corner Bakery"),
                Transaction(LocalDate.of(2026, 3, 4), -700, "Transit Pass"),
            ),
            statement.transactions,
        )
        assertEquals(120_050, statement.balanceCents)
        assertEquals(listOf("imported 3 lines"), audit)
    }

    @Test
    fun brokenStatementReportsEveryProblemInOnePass() {
        val result: EitherNel<ImportError, Statement> = StatementParser.statement(
            """
            de-not-an-iban;GBP
            2026-02-30;12.00;
            2026-03-05;twelve;Hardware Store
            2026-03-06;3.10
            2026-03-07;-1.99;Coffee
            """,
        )

        val errors: NonEmptyList<ImportError> = result.swap().getOrElse { error("expected errors") }
        assertEquals(
            nonEmptyListOf<ImportError>(
                ImportError.BadHeader(
                    1, "IBAN 'de-not-an-iban' is invalid and currency 'GBP' is unsupported",
                ),
                ImportError.BadDate(2, "2026-02-30"),
                ImportError.MissingCounterparty(2),
                ImportError.BadAmount(3, "twelve"),
                ImportError.MalformedLine(4, 2),
            ),
            errors,
        )
        assertTrue(result.isLeft { it.size == 5 })
        assertFalse(result.isRight { true })
        assertEquals(listOf(1, 2, 3, 4), errors.map { it.line }.distinct())
    }

    @Test
    fun catchOrThrowOnlyTurnsTheDeclaredExceptionIntoAnError() {
        assertEquals(ImportError.BadAmount(9, "1,5").left(), StatementParser.amount(9, "1,5"))
        assertEquals(1_999L.right(), StatementParser.amount(9, "19.99"))
        assertThrows<ArithmeticException> { StatementParser.amount(9, "0.005") }
    }

    @Test
    fun branchTotalsAndDailyChecksAreMergedWithoutTheDsl() {
        val berlin: Either<String, Long> = 10_000L.right()
        val zurich: Either<String, Long> = 2_500L.right()
        val vienna: Either<String, Long> = "vienna feed offline".left()
        val lisbon: Either<String, Long> = "lisbon feed stale".left()
        val joinErrors: (String, String) -> String = { first: String, second: String -> "$first, $second" }

        assertEquals(12_500L.right(), berlin.combine(zurich, joinErrors, Long::plus))
        assertEquals("vienna feed offline".left(), berlin.combine(vienna, joinErrors, Long::plus))
        assertEquals(
            "vienna feed offline, lisbon feed stale".left(),
            vienna.combine(lisbon, joinErrors, Long::plus),
        )

        val dailyChecks: List<Either<String, LocalDate>> = listOf(
            LocalDate.of(2026, 3, 1).right(),
            "2026-03-02 missing closing balance".left(),
            LocalDate.of(2026, 3, 3).right(),
            "2026-03-04 duplicate booking".left(),
        )
        assertEquals(
            nonEmptyListOf("2026-03-02 missing closing balance", "2026-03-04 duplicate booking").left(),
            dailyChecks.flattenOrAccumulate(),
        )
        assertEquals(
            "2026-03-02 missing closing balance, 2026-03-04 duplicate booking".left(),
            dailyChecks.flattenOrAccumulate(joinErrors),
        )
        assertEquals(
            listOf(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 3)).right(),
            dailyChecks.filter { it.isRight() }.flattenOrAccumulate(),
        )
    }

    @Test
    fun importOutcomesAreRankedAndSplitForTheOperatorQueue() {
        val outcomes: List<Either<Int, Long>> = listOf(
            500L.right(),
            3.left(),
            (-200L).right(),
            1.left(),
        )
        val ranked: List<Either<Int, Long>> = outcomes.sortedWith { a: Either<Int, Long>, b: Either<Int, Long> ->
            a.compareTo(b)
        }
        assertEquals(listOf(1.left(), 3.left(), (-200L).right(), 500L.right()), ranked)
        assertTrue(1.left() < (-200L).right())

        val reviewed: List<Ior<String, Transaction>> = listOf(
            StatementParser.line(2, "2026-03-01;10.00;Gym").mapLeft { "line 2 rejected" }.toIor(),
            StatementParser.line(3, "2026-03-02;oops;Gym").mapLeft { "line 3 rejected" }.toIor(),
            Ior.Both(
                "line 4 counterparty truncated",
                Transaction(LocalDate.of(2026, 3, 3), -1_500, "Very Long Supplier Na"),
            ),
        )
        val (warnings: List<String>, accepted: List<Transaction>) = reviewed.separateIor()

        assertEquals(listOf("line 3 rejected", "line 4 counterparty truncated"), warnings)
        assertEquals(listOf("Gym", "Very Long Supplier Na"), accepted.map { it.counterparty })
    }
}
