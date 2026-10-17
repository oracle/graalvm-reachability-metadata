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
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.castTo
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.json.contains
import org.jetbrains.exposed.v1.json.exists
import org.jetbrains.exposed.v1.json.extract
import org.jetbrains.exposed.v1.json.jsonb
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.batchInsert
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
 * JSONB query functions on PostgreSQL: a product catalog keeps typed specifications in a `jsonb`
 * column and filters, projects and sorts them with `extract`, `contains` and `exists`.
 */
class ProductCatalogJsonbQueryTest {

    @Serializable
    data class Dimensions(val widthMm: Int, val heightMm: Int)

    @Serializable
    data class ProductSpec(
        val brand: String,
        val category: String,
        val tags: List<String>,
        val dimensions: Dimensions,
        val discountPercent: Int? = null,
    )

    object Products : Table("products") {
        val id = integer("id").autoIncrement()
        val sku = varchar("sku", 32).uniqueIndex()
        val spec = jsonb<ProductSpec>("spec", Json { explicitNulls = false })

        override val primaryKey = PrimaryKey(id)
    }

    companion object {
        private const val PASSWORD = "catalog-secret"
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
    fun filtersProjectsAndSortsByJsonbContent() {
        val database = Database.connect(
            url = jdbcUrl,
            driver = "org.postgresql.Driver",
            user = "postgres",
            password = PASSWORD,
        )
        val catalog: Map<String, ProductSpec> = linkedMapOf(
            "LAMP-01" to ProductSpec("Lumen", "lighting", listOf("indoor", "sale"), Dimensions(300, 450), 15),
            "LAMP-02" to ProductSpec("Lumen", "lighting", listOf("outdoor"), Dimensions(180, 900)),
            "TENT-01" to ProductSpec("Trailhead", "camping", listOf("outdoor", "sale"), Dimensions(2100, 1300), 30),
            "DESK-01" to ProductSpec("Oakline", "furniture", listOf("indoor"), Dimensions(1400, 750)),
        )

        transaction(database) {
            SchemaUtils.create(Products)
            Products.batchInsert(catalog.entries) { (sku, spec) ->
                this[Products.sku] = sku
                this[Products.spec] = spec
            }
        }

        val brand = Products.spec.extract<String>("brand")
        val width = Products.spec.extract<String>("dimensions", "widthMm").castTo(IntegerColumnType())

        val lighting: List<Pair<String, String>> = transaction(database) {
            Products.select(Products.sku, brand)
                .where { Products.spec.extract<String>("category") eq "lighting" }
                .orderBy(Products.sku)
                .map { it[Products.sku] to it[brand] }
        }
        assertThat(lighting).containsExactly("LAMP-01" to "Lumen", "LAMP-02" to "Lumen")

        val outdoorSale: List<String> = transaction(database) {
            Products.select(Products.sku)
                .where { Products.spec.contains("""{"tags":["outdoor","sale"]}""") }
                .map { it[Products.sku] }
        }
        assertThat(outdoorSale).containsExactly("TENT-01")

        val discounted: List<String> = transaction(database) {
            Products.select(Products.sku)
                .where { Products.spec.exists(".discountPercent") }
                .orderBy(Products.sku)
                .map { it[Products.sku] }
        }
        assertThat(discounted).containsExactly("LAMP-01", "TENT-01")

        val wideByWidth: List<Pair<String, Int>> = transaction(database) {
            Products.select(Products.sku, width)
                .where { width greater 250 }
                .orderBy(width to SortOrder.DESC)
                .map { it[Products.sku] to it[width] }
        }
        assertThat(wideByWidth).containsExactly("TENT-01" to 2100, "DESK-01" to 1400, "LAMP-01" to 300)

        val clearance: ProductSpec = catalog.getValue("LAMP-02").copy(tags = listOf("outdoor", "sale"), discountPercent = 40)
        val updatedRows: Int = transaction(database) {
            Products.update({ (Products.sku eq "LAMP-02") and Products.spec.contains("""{"brand":"Lumen"}""") }) {
                it[spec] = clearance
            }
        }
        assertThat(updatedRows).isEqualTo(1)

        val afterClearance: Map<String, ProductSpec> = transaction(database) {
            Products.selectAll()
                .where { Products.spec.contains("""{"tags":["sale"]}""") and Products.spec.exists(".discountPercent") }
                .associate { it[Products.sku] to it[Products.spec] }
        }
        assertThat(afterClearance).containsOnlyKeys("LAMP-01", "LAMP-02", "TENT-01")
        assertThat(afterClearance["LAMP-02"]).isEqualTo(clearance)
        assertThat(afterClearance.getValue("TENT-01").dimensions).isEqualTo(Dimensions(2100, 1300))

        transaction(database) { SchemaUtils.drop(Products) }
    }
}
