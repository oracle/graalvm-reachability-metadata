/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_arrow_kt.arrow_fx_coroutines_jvm

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import arrow.fx.coroutines.parMapOrAccumulate
import arrow.fx.coroutines.parZipOrAccumulate
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.ResultSet
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Exercises Arrow's error-accumulating parallel operators: `parZipOrAccumulate` validating
 * independent parts of one request and `parMapOrAccumulate` validating a batch, both running every
 * branch to completion and reporting all failures together instead of stopping at the first.
 *
 * A procurement service onboards suppliers and their first price list. It checks the tax
 * registry and the bank directory in an in-memory H2 database through a HikariCP pool, so a
 * rejected supplier learns every problem with the submission in one response.
 */
public class SupplierOnboardingValidationTest {

    /** A validation problem reported back to the supplier. */
    public sealed interface Problem {
        public data class UnknownTaxId(val taxId: String) : Problem
        public data class TaxIdSuspended(val taxId: String) : Problem
        public data class UnknownBank(val bic: String) : Problem
        public data class InvalidIban(val iban: String) : Problem
        public data class UnknownSku(val line: Int, val sku: String) : Problem
        public data class PriceBelowFloor(val line: Int, val sku: String, val floorCents: Long) : Problem
    }

    /** What a supplier submits to be onboarded. */
    public data class Registration(val name: String, val taxId: String, val bic: String, val iban: String)

    /** A registered tax entity. */
    public data class TaxEntity(val taxId: String, val legalName: String)

    /** A verified payout account. */
    public data class PayoutAccount(val bankName: String, val iban: String)

    /** A supplier that passed onboarding. */
    public data class Supplier(val legalName: String, val bankName: String, val iban: String)

    /** One line of a submitted price list. */
    public data class PriceLine(val sku: String, val priceCents: Long)

    /** A price line accepted into the catalog. */
    public data class AcceptedPrice(val sku: String, val description: String, val priceCents: Long)

    /** Validation backed by the registry tables, recording which checks actually ran. */
    public class OnboardingService(private val pool: HikariDataSource) {

        /** Every check that ran, keyed by check name, so tests can see no branch was skipped. */
        public val checksRun: MutableSet<String> = ConcurrentHashMap.newKeySet()

        /** Validates the tax and banking parts of [registration] in parallel, reporting both. */
        public suspend fun onboard(registration: Registration): Either<NonEmptyList<Problem>, Supplier> =
            either {
                parZipOrAccumulate(
                    Dispatchers.IO,
                    {
                        checksRun.add("tax:${registration.taxId}")
                        val row: Pair<String, Boolean>? = query(
                            "SELECT legal_name, suspended FROM tax_registry WHERE tax_id = ?",
                            registration.taxId,
                        ) { it.getString(1) to it.getBoolean(2) }
                        ensureNotNull(row) { Problem.UnknownTaxId(registration.taxId) }
                        ensure(!row.second) { Problem.TaxIdSuspended(registration.taxId) }
                        TaxEntity(registration.taxId, row.first)
                    },
                    {
                        checksRun.add("bank:${registration.bic}")
                        val bankName: String? = query(
                            "SELECT name FROM bank_directory WHERE bic = ?",
                            registration.bic,
                        ) { it.getString(1) }
                        ensureNotNull(bankName) { Problem.UnknownBank(registration.bic) }
                        ensure(isValidIban(registration.iban)) { Problem.InvalidIban(registration.iban) }
                        PayoutAccount(bankName, registration.iban)
                    },
                ) { entity, account -> Supplier(entity.legalName, account.bankName, account.iban) }
            }

        /**
         * Validates every price line in parallel, at most [concurrency] at a time; problems are
         * merged into one human-readable report with [joinReports].
         */
        public suspend fun reviewPriceList(
            lines: List<PriceLine>,
            concurrency: Int,
        ): Either<String, List<AcceptedPrice>> =
            lines.withIndex().parMapOrAccumulate(
                Dispatchers.IO,
                concurrency,
                { left: String, right: String -> joinReports(left, right) },
            ) { (index, line) ->
                checksRun.add("price:$index")
                val floor: Pair<String, Long>? = query(
                    "SELECT description, floor_cents FROM catalog WHERE sku = ?",
                    line.sku,
                ) { it.getString(1) to it.getLong(2) }
                ensureNotNull(floor) { "line ${index + 1}: unknown sku ${line.sku}" }
                ensure(line.priceCents >= floor.second) {
                    "line ${index + 1}: ${line.sku} below floor ${floor.second}"
                }
                AcceptedPrice(line.sku, floor.first, line.priceCents)
            }

        /** Validates price lines with typed problems collected into a non-empty list. */
        public suspend fun reviewPriceListTyped(
            lines: List<PriceLine>,
        ): Either<NonEmptyList<Problem>, List<AcceptedPrice>> =
            lines.withIndex().parMapOrAccumulate(Dispatchers.IO) { (index, line) ->
                val floor: Pair<String, Long>? = query(
                    "SELECT description, floor_cents FROM catalog WHERE sku = ?",
                    line.sku,
                ) { it.getString(1) to it.getLong(2) }
                ensureNotNull(floor) { Problem.UnknownSku(index + 1, line.sku) }
                ensure(line.priceCents >= floor.second) {
                    Problem.PriceBelowFloor(index + 1, line.sku, floor.second)
                }
                AcceptedPrice(line.sku, floor.first, line.priceCents)
            }

        private fun isValidIban(iban: String): Boolean =
            iban.length in 15..34 && iban.take(2).all { it.isLetter() } && iban.drop(2).all { it.isLetterOrDigit() }

        private fun <T> query(sql: String, key: String, read: (ResultSet) -> T): T? =
            pool.connection.use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, key)
                    statement.executeQuery().use { rows -> if (rows.next()) read(rows) else null }
                }
            }

        private companion object {
            fun joinReports(left: String, right: String): String = "$left; $right"
        }
    }

    private lateinit var pool: HikariDataSource
    private lateinit var service: OnboardingService

    @BeforeEach
    public fun openRegistry() {
        val config = HikariConfig()
        config.jdbcUrl = "jdbc:h2:mem:onboarding;DB_CLOSE_DELAY=-1"
        config.maximumPoolSize = 4
        pool = HikariDataSource(config)
        pool.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TABLE tax_registry (tax_id VARCHAR(20) PRIMARY KEY, legal_name VARCHAR(100), suspended BOOLEAN);
                    CREATE TABLE bank_directory (bic VARCHAR(11) PRIMARY KEY, name VARCHAR(100));
                    CREATE TABLE catalog (sku VARCHAR(20) PRIMARY KEY, description VARCHAR(100), floor_cents BIGINT);
                    INSERT INTO tax_registry VALUES ('DE811', 'Nordwerk GmbH', FALSE), ('FR442', 'Atelier Brun SARL', TRUE);
                    INSERT INTO bank_directory VALUES ('DEUTDEFF', 'Deutsche Bank'), ('BNPAFRPP', 'BNP Paribas');
                    INSERT INTO catalog VALUES ('BOLT-M8', 'M8 hex bolt', 12), ('NUT-M8', 'M8 hex nut', 5), ('WASH-M8', 'M8 washer', 3);
                    """.trimIndent(),
                )
            }
        }
        service = OnboardingService(pool)
    }

    @AfterEach
    public fun closeRegistry() {
        pool.connection.use { connection -> connection.createStatement().use { it.execute("DROP ALL OBJECTS") } }
        pool.close()
    }

    @Test
    public fun acceptsAValidSupplierByCombiningBothChecks(): Unit = runBlocking {
        val result = service.onboard(Registration("Nordwerk", "DE811", "DEUTDEFF", "DE89370400440532013000"))

        assertEquals(Either.Right(Supplier("Nordwerk GmbH", "Deutsche Bank", "DE89370400440532013000")), result)
        assertEquals(setOf("tax:DE811", "bank:DEUTDEFF"), service.checksRun)
    }

    @Test
    public fun reportsTaxAndBankProblemsTogether(): Unit = runBlocking {
        val result = service.onboard(Registration("Brun", "FR442", "NOPEXXXX", "FR76"))

        assertEquals(
            Either.Left(nonEmptyListOf(Problem.TaxIdSuspended("FR442"), Problem.UnknownBank("NOPEXXXX"))),
            result,
        )
        assertEquals(setOf("tax:FR442", "bank:NOPEXXXX"), service.checksRun)
    }

    @Test
    public fun reportsASingleFailingBranchAlone(): Unit = runBlocking {
        val result = service.onboard(Registration("Ghost", "XX000", "BNPAFRPP", "FR7630006000011234567890189"))

        assertEquals(Either.Left(nonEmptyListOf(Problem.UnknownTaxId("XX000"))), result)
    }

    @Test
    public fun acceptsAPriceListInSubmissionOrder(): Unit = runBlocking {
        val lines = listOf(PriceLine("WASH-M8", 4), PriceLine("BOLT-M8", 15), PriceLine("NUT-M8", 5))

        val result = service.reviewPriceList(lines, concurrency = 2)

        assertEquals(
            Either.Right(
                listOf(
                    AcceptedPrice("WASH-M8", "M8 washer", 4),
                    AcceptedPrice("BOLT-M8", "M8 hex bolt", 15),
                    AcceptedPrice("NUT-M8", "M8 hex nut", 5),
                ),
            ),
            result,
        )
    }

    @Test
    public fun mergesEveryRejectedLineIntoOneReport(): Unit = runBlocking {
        val lines = listOf(
            PriceLine("BOLT-M8", 10),
            PriceLine("NUT-M8", 6),
            PriceLine("RIVET-4", 2),
            PriceLine("WASH-M8", 1),
        )

        val result = service.reviewPriceList(lines, concurrency = 2)

        assertTrue(result.isLeft())
        val report: String = result.leftOrNull()!!
        assertEquals(
            setOf("line 1: BOLT-M8 below floor 12", "line 3: unknown sku RIVET-4", "line 4: WASH-M8 below floor 3"),
            report.split("; ").toSet(),
        )
        assertEquals(setOf("price:0", "price:1", "price:2", "price:3"), service.checksRun)
    }

    @Test
    public fun collectsTypedProblemsForEveryRejectedLine(): Unit = runBlocking {
        val lines = listOf(PriceLine("NUT-M8", 1), PriceLine("BOLT-M8", 20), PriceLine("SCREW-6", 9))

        val result = service.reviewPriceListTyped(lines)

        val problems: NonEmptyList<Problem> = result.leftOrNull()!!
        assertEquals(
            setOf(Problem.PriceBelowFloor(1, "NUT-M8", 5), Problem.UnknownSku(3, "SCREW-6")),
            problems.toSet(),
        )
        assertEquals(2, problems.size)
    }
}
