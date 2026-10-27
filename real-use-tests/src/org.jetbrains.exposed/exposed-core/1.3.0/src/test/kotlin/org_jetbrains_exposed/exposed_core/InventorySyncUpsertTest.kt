/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_exposed.exposed_core

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.core.stringLiteral
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.batchUpsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import org.junit.jupiter.api.Test

/**
 * Upsert and insert-from-select write statements: a warehouse feed synchronises stock levels with
 * single and batched upserts keyed on a natural key, and archives low-stock items with
 * `INSERT ... SELECT`.
 */
class InventorySyncUpsertTest {

    object Stock : Table("stock") {
        val sku = varchar("sku", 32)
        val name = varchar("name", 64)
        val quantity = integer("quantity")
        val revision = integer("revision").default(1)

        override val primaryKey = PrimaryKey(sku)
    }

    object ReorderQueue : Table("reorder_queue") {
        val sku = varchar("sku", 32)
        val reason = varchar("reason", 32)

        override val primaryKey = PrimaryKey(sku)
    }

    data class FeedLine(val sku: String, val name: String, val quantity: Int)

    data class StockLevel(val sku: String, val name: String, val quantity: Int, val revision: Int)

    @Test
    fun synchronisesStockLevelsWithUpsertsAndQueuesReorders() {
        val database: Database = Database.connect(
            url = "jdbc:h2:mem:inventory_sync;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        transaction(database) {
            SchemaUtils.create(Stock, ReorderQueue)
            Stock.insert {
                it[sku] = "A-100"
                it[name] = "Anchor bolt"
                it[quantity] = 40
            }
        }

        transaction(database) {
            // A delivery adds to the existing stock instead of overwriting it.
            Stock.upsert(
                onUpdate = {
                    it[Stock.quantity] = Stock.quantity + insertValue(Stock.quantity)
                    it[Stock.revision] = Stock.revision + 1
                },
            ) {
                it[sku] = "A-100"
                it[name] = "Anchor bolt"
                it[quantity] = 15
            }
            // An unknown SKU is inserted with the column default for its revision.
            Stock.upsert {
                it[sku] = "B-200"
                it[name] = "Bracket"
                it[quantity] = 3
            }
        }

        val feed: List<FeedLine> = listOf(
            FeedLine("B-200", "Bracket, galvanised", 2),
            FeedLine("C-300", "Clamp", 120),
            FeedLine("D-400", "Dowel", 1),
        )
        val levels: List<StockLevel> = transaction(database) {
            // A full feed replaces quantities and names but never rewinds the revision counter.
            Stock.batchUpsert(feed, Stock.sku, onUpdateExclude = listOf(Stock.revision)) { line ->
                this[Stock.sku] = line.sku
                this[Stock.name] = line.name
                this[Stock.quantity] = line.quantity
            }
            Stock.selectAll()
                .orderBy(Stock.sku to SortOrder.ASC)
                .map { row -> StockLevel(row[Stock.sku], row[Stock.name], row[Stock.quantity], row[Stock.revision]) }
        }

        assertThat(levels).containsExactly(
            StockLevel("A-100", "Anchor bolt", 55, 2),
            StockLevel("B-200", "Bracket, galvanised", 2, 1),
            StockLevel("C-300", "Clamp", 120, 1),
            StockLevel("D-400", "Dowel", 1, 1),
        )

        val queued: List<Pair<String, String>> = transaction(database) {
            val lowStock = Stock
                .select(Stock.sku, stringLiteral("LOW_STOCK"))
                .where { Stock.quantity less 5 }
            val inserted: Int? = ReorderQueue.insert(lowStock, columns = listOf(ReorderQueue.sku, ReorderQueue.reason))
            assertThat(inserted).isEqualTo(2)
            ReorderQueue.selectAll()
                .orderBy(ReorderQueue.sku to SortOrder.ASC)
                .map { row -> row[ReorderQueue.sku] to row[ReorderQueue.reason] }
        }
        assertThat(queued).containsExactly("B-200" to "LOW_STOCK", "D-400" to "LOW_STOCK")

        val wellStocked: Long = transaction(database) {
            Stock.selectAll().where { Stock.quantity greater 50 }.count()
        }
        assertThat(wellStocked).isEqualTo(2)

        transaction(database) {
            SchemaUtils.drop(ReorderQueue, Stock)
        }
    }
}
