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
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.castTo
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.json.exists
import org.jetbrains.exposed.v1.json.extract
import org.jetbrains.exposed.v1.json.jsonb
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * JSONPath filter predicates and reporting over extracted JSON fields on PostgreSQL: a
 * fulfillment service selects shipments with parameterized `exists` JSONPath filters, then
 * groups, aggregates and joins them on fields extracted from their `jsonb` manifests.
 */
class FulfillmentManifestJsonPathReportTest {

    @Serializable
    data class Line(val sku: String, val qty: Int, val hazmat: Boolean = false)

    @Serializable
    data class Manifest(
        val warehouse: String,
        val region: String,
        val weightKg: Int,
        val lines: List<Line>,
    )

    object Warehouses : Table("warehouses") {
        val code = varchar("code", 8)
        val city = varchar("city", 64)

        override val primaryKey = PrimaryKey(code)
    }

    object Shipments : Table("shipments") {
        val id = integer("id").autoIncrement()
        val reference = varchar("reference", 16).uniqueIndex()
        val manifest = jsonb<Manifest>("manifest", Json)

        override val primaryKey = PrimaryKey(id)
    }

    companion object {
        private const val PASSWORD = "fulfillment-secret"
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

        /** Polls with a bounded deadline until the server accepts TCP connections. */
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
    fun filtersWithJsonPathAndReportsOnExtractedFields() {
        val database = Database.connect(
            url = jdbcUrl,
            driver = "org.postgresql.Driver",
            user = "postgres",
            password = PASSWORD,
        )
        val shipments: Map<String, Manifest> = linkedMapOf(
            "SHP-100" to Manifest("BER1", "eu", 12, listOf(Line("CAB-1", 4), Line("BAT-9", 12, hazmat = true))),
            "SHP-101" to Manifest("BER1", "eu", 3, listOf(Line("CAB-1", 1))),
            "SHP-102" to Manifest("NYC2", "us", 40, listOf(Line("DSK-7", 2), Line("CAB-1", 25))),
            "SHP-103" to Manifest("AMS1", "eu", 7, listOf(Line("BAT-9", 3, hazmat = true))),
            "SHP-104" to Manifest("NYC2", "us", 18, listOf(Line("LMP-2", 9))),
        )

        transaction(database) {
            SchemaUtils.create(Warehouses, Shipments)
            Warehouses.batchInsert(listOf("BER1" to "Berlin", "AMS1" to "Amsterdam", "NYC2" to "New York")) {
                (code, city) ->
                this[Warehouses.code] = code
                this[Warehouses.city] = city
            }
            Shipments.batchInsert(shipments.entries) { (reference, manifest) ->
                this[Shipments.reference] = reference
                this[Shipments.manifest] = manifest
            }
        }

        // A JSONPath filter whose threshold is passed as a jsonpath variable, not spliced into the path.
        val bulkLines: (Int) -> List<String> = { minimum ->
            transaction(database) {
                Shipments.select(Shipments.reference)
                    .where { Shipments.manifest.exists(".lines[*] ? (@.qty >= \$min)", optional = """{"min": $minimum}""") }
                    .orderBy(Shipments.reference)
                    .map { it[Shipments.reference] }
            }
        }
        assertThat(bulkLines(10)).containsExactly("SHP-100", "SHP-102")
        assertThat(bulkLines(5)).containsExactly("SHP-100", "SHP-102", "SHP-104")
        assertThat(bulkLines(100)).isEmpty()

        val hazmatInEurope: List<String> = transaction(database) {
            Shipments.select(Shipments.reference)
                .where { Shipments.manifest.exists(" ? (@.region == \"eu\").lines[*] ? (@.hazmat == true)") }
                .orderBy(Shipments.reference)
                .map { it[Shipments.reference] }
        }
        assertThat(hazmatInEurope).containsExactly("SHP-100", "SHP-103")

        val region = Shipments.manifest.extract<String>("region")
        val weight = Shipments.manifest.extract<String>("weightKg").castTo(IntegerColumnType())
        val shipmentCount = Shipments.id.count()
        val totalWeight = weight.sum()
        val byRegion: Map<String, Pair<Long, Int?>> = transaction(database) {
            Shipments.select(region, shipmentCount, totalWeight)
                .groupBy(region)
                .orderBy(region)
                .associate { it[region] to (it[shipmentCount] to it[totalWeight]) }
        }
        assertThat(byRegion).containsExactlyEntriesOf(linkedMapOf("eu" to (3L to 22), "us" to (2L to 58)))

        val warehouseCode = Shipments.manifest.extract<String>("warehouse")
        val perCity: List<Pair<String, Long>> = transaction(database) {
            Shipments.join(Warehouses, JoinType.INNER, additionalConstraint = { warehouseCode eq Warehouses.code })
                .select(Warehouses.city, shipmentCount)
                .groupBy(Warehouses.city)
                .orderBy(shipmentCount to SortOrder.DESC, Warehouses.city to SortOrder.ASC)
                .map { it[Warehouses.city] to it[shipmentCount] }
        }
        assertThat(perCity).containsExactly("Berlin" to 2L, "New York" to 2L, "Amsterdam" to 1L)

        transaction(database) { SchemaUtils.drop(Shipments, Warehouses) }
    }
}
