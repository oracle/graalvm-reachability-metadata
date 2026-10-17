/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_exposed.exposed_jdbc

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.minus
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.javatime.timestamp
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.batchUpsert
import org.jetbrains.exposed.v1.jdbc.deleteReturning
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.updateReturning
import org.jetbrains.exposed.v1.jdbc.upsert
import org.jetbrains.exposed.v1.jdbc.upsertReturning
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.sql.DriverManager
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Bulk writes with conflict handling and `RETURNING` on PostgreSQL: an inventory sync job loads a
 * supplier feed with `batchInsert`, `insertIgnore`, `upsert` and `batchUpsert`, then reads changed
 * rows straight back from `insertReturning`, `upsertReturning`, `updateReturning` and `deleteReturning`.
 */
class InventorySyncUpsertReturningTest {

    object Products : Table("products") {
        val sku = varchar("sku", 32)
        val name = varchar("name", 64)
        val stock = integer("stock")
        val priceCents = long("price_cents")
        val syncedAt = timestamp("synced_at")

        override val primaryKey = PrimaryKey(sku)
    }

    object StockEvents : Table("stock_events") {
        val id = integer("id").autoIncrement()
        val sku = varchar("sku", 32)
        val delta = integer("delta")

        override val primaryKey = PrimaryKey(id)
    }

    data class FeedItem(val sku: String, val name: String, val stock: Int, val priceCents: Long)

    companion object {
        private const val PASSWORD = "inventory-secret"
        private val containerName: String = "real-use-exposed-jdbc-inventory-" + UUID.randomUUID()
        private val FIRST_SYNC: Instant = Instant.parse("2026-03-01T08:00:00Z")
        private val SECOND_SYNC: Instant = Instant.parse("2026-03-02T08:00:00Z")
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
    fun syncsSupplierFeedWithConflictHandlingAndReturningClauses() {
        val database = Database.connect(
            url = jdbcUrl,
            driver = "org.postgresql.Driver",
            user = "postgres",
            password = PASSWORD,
        )

        fun inventory(): Map<String, Pair<Int, Long>> = transaction(database) {
            Products.selectAll().associate { it[Products.sku] to (it[Products.stock] to it[Products.priceCents]) }
        }

        val initialFeed = listOf(
            FeedItem("KB-01", "Mechanical keyboard", 12, 8_900L),
            FeedItem("MS-02", "Wireless mouse", 40, 2_500L),
            FeedItem("HD-03", "USB hub", 3, 1_900L),
        )
        val loadedSkus: List<String> = transaction(database) {
            SchemaUtils.create(Products, StockEvents)
            Products.batchInsert(initialFeed) { item ->
                this[Products.sku] = item.sku
                this[Products.name] = item.name
                this[Products.stock] = item.stock
                this[Products.priceCents] = item.priceCents
                this[Products.syncedAt] = FIRST_SYNC
            }.map { it[Products.sku] }
        }
        assertThat(loadedSkus).containsExactly("KB-01", "MS-02", "HD-03")

        val ignoredCount: Int = transaction(database) {
            Products.insertIgnore {
                it[sku] = "KB-01"
                it[name] = "Duplicate keyboard"
                it[stock] = 999
                it[priceCents] = 1L
                it[syncedAt] = SECOND_SYNC
            }.insertedCount
        }
        assertThat(ignoredCount).isZero()
        assertThat(inventory()["KB-01"]).isEqualTo(12 to 8_900L)

        transaction(database) {
            Products.upsert(onUpdate = { it[Products.stock] = Products.stock + insertValue(Products.stock) }) {
                it[sku] = "MS-02"
                it[name] = "Wireless mouse"
                it[stock] = 10
                it[priceCents] = 2_500L
                it[syncedAt] = SECOND_SYNC
            }
        }
        assertThat(inventory()["MS-02"]).isEqualTo(50 to 2_500L)

        val repricing = listOf(
            FeedItem("KB-01", "Mechanical keyboard v2", 0, 9_400L),
            FeedItem("CB-04", "USB-C cable", 25, 900L),
        )
        transaction(database) {
            Products.batchUpsert(repricing, onUpdateExclude = listOf(Products.stock)) { item ->
                this[Products.sku] = item.sku
                this[Products.name] = item.name
                this[Products.stock] = item.stock
                this[Products.priceCents] = item.priceCents
                this[Products.syncedAt] = SECOND_SYNC
            }
        }
        assertThat(inventory()).containsExactlyInAnyOrderEntriesOf(
            mapOf(
                "KB-01" to (12 to 9_400L),
                "MS-02" to (50 to 2_500L),
                "HD-03" to (3 to 1_900L),
                "CB-04" to (25 to 900L),
            ),
        )

        val guardedPrice: List<Long> = transaction(database) {
            Products.upsertReturning(
                returning = listOf(Products.priceCents),
                where = { Products.priceCents less 1_500L },
            ) {
                it[sku] = "HD-03"
                it[name] = "USB hub"
                it[stock] = 3
                it[priceCents] = 1_500L
                it[syncedAt] = SECOND_SYNC
            }.map { it[Products.priceCents] }
        }
        assertThat(guardedPrice).isEmpty()
        assertThat(inventory()["HD-03"]).isEqualTo(3 to 1_900L)

        val eventIds: List<Pair<Int, Int>> = transaction(database) {
            listOf("HD-03" to -3, "CB-04" to -5).map { (eventSku, eventDelta) ->
                StockEvents.insertReturning(listOf(StockEvents.id, StockEvents.delta)) {
                    it[sku] = eventSku
                    it[delta] = eventDelta
                }.single().let { it[StockEvents.id] to it[StockEvents.delta] }
            }
        }
        assertThat(eventIds).containsExactly(1 to -3, 2 to -5)

        val reserved: Map<String, Int> = transaction(database) {
            Products.updateReturning(
                returning = listOf(Products.sku, Products.stock),
                where = { Products.sku eq "HD-03" or (Products.sku eq "CB-04") },
            ) {
                it[stock] = stock - 3
                it[syncedAt] = SECOND_SYNC
            }.associate { it[Products.sku] to it[Products.stock] }
        }
        assertThat(reserved).containsExactlyInAnyOrderEntriesOf(mapOf("HD-03" to 0, "CB-04" to 22))

        val discontinued: List<Pair<String, Instant>> = transaction(database) {
            Products.deleteReturning(listOf(Products.sku, Products.syncedAt)) { Products.stock lessEq 0 }
                .map { it[Products.sku] to it[Products.syncedAt] }
        }
        assertThat(discontinued).containsExactly("HD-03" to SECOND_SYNC)

        val remaining: List<String> = transaction(database) {
            Products.selectAll()
                .where { Products.stock greater 0 }
                .orderBy(Products.sku)
                .map { it[Products.sku] }
        }
        assertThat(remaining).containsExactly("CB-04", "KB-01", "MS-02")

        transaction(database) { SchemaUtils.drop(StockEvents, Products) }
    }
}
