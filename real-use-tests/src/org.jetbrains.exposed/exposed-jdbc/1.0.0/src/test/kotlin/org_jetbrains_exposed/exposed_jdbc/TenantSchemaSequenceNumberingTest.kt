/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_exposed.exposed_jdbc

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.NextVal
import org.jetbrains.exposed.v1.core.Schema
import org.jetbrains.exposed.v1.core.Sequence
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.nextLongVal
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.exists
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.vendors.currentDialectMetadata
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Database schemas and sequences: a multi-tenant invoicing service on PostgreSQL gives each tenant
 * its own schema through `DatabaseConfig.defaultSchema`, numbers invoices from a sequence-backed
 * `autoIncrement` column, draws ticket numbers from a cycling standalone `Sequence`, and tears the
 * tenants down with `SchemaUtils.dropSchema`.
 */
class TenantSchemaSequenceNumberingTest {

    object InvoiceNumbers {
        val sequence = Sequence("invoice_numbers", startWith = 1000, incrementBy = 10)
    }

    /** One logical table that resolves to a different physical table in every tenant schema. */
    object Invoices : Table("invoices") {
        val number = long("number").autoIncrement(InvoiceNumbers.sequence)
        val customer = varchar("customer", 64)
        val amountCents = long("amount_cents")

        override val primaryKey = PrimaryKey(number)
    }

    /** The same table addressed with an explicit schema, as a back-office report would. */
    object AcmeInvoices : Table("tenant_acme.invoices") {
        val number = long("number")
        val customer = varchar("customer", 64)
    }

    data class Tenant(val schema: Schema, val database: Database)

    companion object {
        private const val PASSWORD = "tenants-secret"
        private val containerName: String = "real-use-exposed-jdbc-tenants-" + UUID.randomUUID()
        private val ACME = Schema("tenant_acme")
        private val GLOBEX = Schema("tenant_globex")
        private val TICKETS = Sequence("support_tickets", startWith = 1, incrementBy = 1, minValue = 1, maxValue = 3, cycle = true)
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

        private fun connect(defaultSchema: Schema? = null): Database = Database.connect(
            url = jdbcUrl,
            driver = "org.postgresql.Driver",
            user = "postgres",
            password = PASSWORD,
            databaseConfig = DatabaseConfig { this.defaultSchema = defaultSchema },
        )
    }

    @Test
    fun isolatesTenantsInSchemasAndNumbersRecordsFromSequences() {
        val admin: Database = connect()
        transaction(admin) { SchemaUtils.createSchema(ACME, GLOBEX) }
        assertThat(transaction(admin) { listOf(ACME.exists(), GLOBEX.exists()) }).containsExactly(true, true)

        val tenants: List<Tenant> = listOf(ACME, GLOBEX).map { Tenant(it, connect(defaultSchema = it)) }
        tenants.forEach { tenant ->
            transaction(tenant.database) {
                SchemaUtils.createSequence(InvoiceNumbers.sequence)
                SchemaUtils.create(Invoices)
            }
        }
        val (acme: Tenant, globex: Tenant) = tenants

        fun bill(tenant: Tenant, customer: String, amountCents: Long): Long = transaction(tenant.database) {
            Invoices.insert {
                it[Invoices.customer] = customer
                it[Invoices.amountCents] = amountCents
            } get Invoices.number
        }

        assertThat(bill(acme, "Wile E. Coyote", 12_500L)).isEqualTo(1000L)
        assertThat(bill(acme, "Road Runner Inc", 900L)).isEqualTo(1010L)
        assertThat(bill(globex, "Hank Scorpio", 75_000L)).isEqualTo(1000L)
        assertThat(bill(acme, "Marvin", 4_200L)).isEqualTo(1020L)

        fun ledger(tenant: Tenant): List<Pair<Long, String>> = transaction(tenant.database) {
            Invoices.selectAll()
                .orderBy(Invoices.number to SortOrder.ASC)
                .map { it[Invoices.number] to it[Invoices.customer] }
        }
        assertThat(ledger(acme)).containsExactly(
            1000L to "Wile E. Coyote",
            1010L to "Road Runner Inc",
            1020L to "Marvin",
        )
        assertThat(ledger(globex)).containsExactly(1000L to "Hank Scorpio")

        val acmeSequences: List<String> = transaction(acme.database) {
            assertThat(InvoiceNumbers.sequence.exists()).isTrue()
            currentDialectMetadata.sequences().map { it.substringAfterLast('.').lowercase() }
        }
        assertThat(acmeSequences).contains("invoice_numbers")

        val report: List<Pair<Long, String>> = transaction(admin) {
            assertThat(AcmeInvoices.schemaName).isEqualTo("tenant_acme")
            assertThat(SchemaUtils.listTablesInAllSchemas().map { it.lowercase() })
                .contains("tenant_acme.invoices", "tenant_globex.invoices")
            AcmeInvoices.select(AcmeInvoices.number, AcmeInvoices.customer)
                .where { AcmeInvoices.customer like "%Inc" }
                .map { it[AcmeInvoices.number] to it[AcmeInvoices.customer] }
        }
        assertThat(report).containsExactly(1010L to "Road Runner Inc")

        val nextTicket: NextVal<Long> = TICKETS.nextLongVal()
        val tickets: List<Long> = transaction(admin) {
            SchemaUtils.createSequence(TICKETS)
            assertThat(TICKETS.exists()).isTrue()
            List(5) { Table.Dual.select(nextTicket).single()[nextTicket] }
        }
        assertThat(tickets).containsExactly(1L, 2L, 3L, 1L, 2L)

        transaction(admin) {
            SchemaUtils.dropSequence(TICKETS)
            SchemaUtils.dropSchema(ACME, GLOBEX, cascade = true)
        }
        val leftovers: List<Boolean> = transaction(admin) {
            listOf(TICKETS.exists(), ACME.exists(), GLOBEX.exists())
        }
        assertThat(leftovers).containsExactly(false, false, false)
    }
}
