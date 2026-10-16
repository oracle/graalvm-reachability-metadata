/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_exposed.exposed_json

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.BooleanColumnType
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.castTo
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.json.contains
import org.jetbrains.exposed.v1.json.extract
import org.jetbrains.exposed.v1.json.jsonb
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Server-side JSON data migration on PostgreSQL: a signup service purges archived `jsonb`
 * documents with `deleteWhere`, copies the rest into a relational table with an insert-select
 * over extracted fields, and backfills columns with updates whose values are JSON expressions.
 */
class SignupMigrationJsonBackfillTest {

    @Serializable
    data class Address(val city: String, val country: String)

    @Serializable
    data class Plan(val code: String, val seats: Int)

    @Serializable
    data class Signup(
        val email: String,
        val plan: Plan,
        val address: Address,
        val marketingOptIn: Boolean,
        val archived: Boolean = false,
    )

    object LegacySignups : Table("legacy_signups") {
        val id = integer("id")
        val payload = jsonb<Signup>("payload", Json)

        override val primaryKey = PrimaryKey(id)
    }

    object Customers : Table("customers") {
        val id = integer("id")
        val email = varchar("email", 128)
        val planCode = varchar("plan_code", 16)
        val seats = integer("seats")
        val address = jsonb<Address>("address", Json)
        val country = varchar("country", 2).nullable()
        val marketingOptIn = bool("marketing_opt_in").default(false)

        override val primaryKey = PrimaryKey(id)
    }

    /** What the application reads back for a migrated customer. */
    data class Customer(
        val email: String,
        val planCode: String,
        val seats: Int,
        val address: Address,
        val country: String?,
        val marketingOptIn: Boolean,
    )

    companion object {
        private const val PASSWORD = "migration-secret"
        private val containerName: String = "real-use-exposed-json-" + UUID.randomUUID()
        private var port: Int = 0

        private val jdbcUrl: String
            get() = "jdbc:postgresql://127.0.0.1:$port/postgres"

        @BeforeAll
        @JvmStatic
        fun startPostgres() {
            port = ServerSocket(0).use { it.localPort }
            docker(
                "run", "--rm", "-d", "--name", containerName,
                "-e", "POSTGRES_PASSWORD=$PASSWORD",
                "-p", "127.0.0.1:$port:5432",
                "postgres:18-alpine",
            )
            awaitConnection(TimeUnit.SECONDS.toMillis(120))
        }

        @AfterAll
        @JvmStatic
        fun stopPostgres() {
            docker("rm", "-f", containerName)
        }

        private fun docker(vararg args: String) {
            val process = ProcessBuilder(listOf("docker") + args).inheritIO().start()
            check(process.waitFor(5, TimeUnit.MINUTES)) { "docker ${args.first()} timed out" }
            check(process.exitValue() == 0) { "docker ${args.first()} failed with ${process.exitValue()}" }
        }

        /** Polls with a bounded deadline until the server accepts connections. */
        private fun awaitConnection(timeoutMillis: Long) {
            val deadline = System.currentTimeMillis() + timeoutMillis
            var lastFailure: Exception? = null
            while (System.currentTimeMillis() < deadline) {
                try {
                    DriverManager.getConnection(jdbcUrl, "postgres", PASSWORD).use { connection ->
                        connection.createStatement().use { it.execute("SELECT 1") }
                    }
                    return
                } catch (e: Exception) {
                    lastFailure = e
                    Thread.sleep(250)
                }
            }
            throw IllegalStateException("PostgreSQL did not become reachable", lastFailure)
        }
    }

    @Test
    fun migratesJsonDocumentsIntoRelationalColumns() {
        val database = Database.connect(
            url = jdbcUrl,
            driver = "org.postgresql.Driver",
            user = "postgres",
            password = PASSWORD,
        )
        val signups: Map<Int, Signup> = linkedMapOf(
            1 to Signup("ada@example.com", Plan("team", 5), Address("London", "GB"), marketingOptIn = true),
            2 to Signup("linus@example.com", Plan("solo", 1), Address("Helsinki", "FI"), marketingOptIn = false),
            3 to Signup("grace@example.com", Plan("team", 12), Address("Arlington", "US"), marketingOptIn = true),
            4 to Signup(
                "old@example.com", Plan("team", 3), Address("Paris", "FR"),
                marketingOptIn = true, archived = true,
            ),
            5 to Signup("trial@example.com", Plan("trial", 0), Address("Oslo", "NO"), marketingOptIn = false),
        )

        transaction(database) {
            SchemaUtils.create(LegacySignups, Customers)
            LegacySignups.batchInsert(signups.entries) { (id, signup) ->
                this[LegacySignups.id] = id
                this[LegacySignups.payload] = signup
            }
        }

        // Purge archived documents first, matching them by JSON containment.
        val purged: Int = transaction(database) {
            LegacySignups.deleteWhere { payload.contains("""{"archived":true}""") }
        }
        assertThat(purged).isEqualTo(1)

        // Copy paying signups in one INSERT ... SELECT, splitting each document into columns.
        val seats = LegacySignups.payload.extract<String>("plan", "seats").castTo(IntegerColumnType())
        val copied: Int? = transaction(database) {
            Customers.insert(
                LegacySignups.select(
                    LegacySignups.id,
                    LegacySignups.payload.extract<String>("email"),
                    LegacySignups.payload.extract<String>("plan", "code"),
                    seats,
                    LegacySignups.payload.extract<Address>("address", toScalar = false),
                ).where { seats greaterEq 1 },
                columns = listOf(Customers.id, Customers.email, Customers.planCode, Customers.seats, Customers.address),
            )
        }
        assertThat(copied).isEqualTo(3)

        // Backfill the country column from the migrated address sub-document.
        val backfilled: Int = transaction(database) {
            Customers.update({ Customers.country.isNull() }) {
                it[country] = address.extract<String>("country")
            }
        }
        assertThat(backfilled).isEqualTo(3)

        // Carry the marketing consent over with an UPDATE ... FROM against the legacy documents.
        val consented: Int = transaction(database) {
            Customers.join(LegacySignups, JoinType.INNER, Customers.id, LegacySignups.id)
                .update({ LegacySignups.payload.contains("""{"marketingOptIn":true}""") }) {
                    it[Customers.marketingOptIn] =
                        LegacySignups.payload.extract<String>("marketingOptIn").castTo(BooleanColumnType())
                }
        }
        assertThat(consented).isEqualTo(2)

        val customers: Map<Int, Customer> = transaction(database) {
            Customers.selectAll()
                .orderBy(Customers.id)
                .associate { row ->
                    row[Customers.id] to Customer(
                        email = row[Customers.email],
                        planCode = row[Customers.planCode],
                        seats = row[Customers.seats],
                        address = row[Customers.address],
                        country = row[Customers.country],
                        marketingOptIn = row[Customers.marketingOptIn],
                    )
                }
        }
        assertThat(customers).containsExactlyEntriesOf(
            linkedMapOf(
                1 to Customer("ada@example.com", "team", 5, Address("London", "GB"), "GB", true),
                2 to Customer("linus@example.com", "solo", 1, Address("Helsinki", "FI"), "FI", false),
                3 to Customer("grace@example.com", "team", 12, Address("Arlington", "US"), "US", true),
            ),
        )

        val remainingLegacy: List<String> = transaction(database) {
            LegacySignups.selectAll().orderBy(LegacySignups.id).map { it[LegacySignups.payload].email }
        }
        assertThat(remainingLegacy)
            .containsExactly("ada@example.com", "linus@example.com", "grace@example.com", "trial@example.com")

        transaction(database) { SchemaUtils.drop(Customers, LegacySignups) }
    }
}
