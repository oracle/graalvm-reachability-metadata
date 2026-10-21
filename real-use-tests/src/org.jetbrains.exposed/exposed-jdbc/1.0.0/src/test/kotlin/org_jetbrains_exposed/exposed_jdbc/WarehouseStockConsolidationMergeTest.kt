/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_exposed.exposed_jdbc

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.except
import org.jetbrains.exposed.v1.jdbc.explain
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.intersect
import org.jetbrains.exposed.v1.jdbc.mergeFrom
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.union
import org.jetbrains.exposed.v1.jdbc.unionAll
import org.junit.jupiter.api.Test

/**
 * Set operations, `INSERT ... SELECT` and `MERGE`: a nightly job consolidates the stock of two
 * warehouses into a network-wide table, compares their assortments with `union`, `unionAll`,
 * `intersect` and `except`, removes recalled products through a merge and explains the report query.
 */
class WarehouseStockConsolidationMergeTest {

    object EastStock : Table("east_stock") {
        val sku = varchar("sku", 16)
        val quantity = integer("quantity")

        override val primaryKey = PrimaryKey(sku)
    }

    object WestStock : Table("west_stock") {
        val sku = varchar("sku", 16)
        val quantity = integer("quantity")

        override val primaryKey = PrimaryKey(sku)
    }

    object Recalls : Table("recalls") {
        val sku = varchar("sku", 16)
        val reason = varchar("reason", 64)

        override val primaryKey = PrimaryKey(sku)
    }

    object NetworkStock : Table("network_stock") {
        val sku = varchar("sku", 16)
        val quantity = integer("quantity")

        override val primaryKey = PrimaryKey(sku)
    }

    @Test
    fun consolidatesWarehouseStockWithSetOperationsAndMerge() {
        val database = Database.connect(
            url = "jdbc:h2:mem:warehouse_stock;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )

        transaction(database) {
            SchemaUtils.create(EastStock, WestStock, Recalls, NetworkStock)

            EastStock.batchInsert(listOf("bolt" to 120, "nut" to 300, "gear" to 15, "valve" to 4)) { (sku, qty) ->
                this[EastStock.sku] = sku
                this[EastStock.quantity] = qty
            }
            WestStock.batchInsert(listOf("nut" to 80, "gear" to 25, "pump" to 7, "hose" to 60)) { (sku, qty) ->
                this[WestStock.sku] = sku
                this[WestStock.quantity] = qty
            }
            Recalls.insert {
                it[sku] = "valve"
                it[reason] = "cracked housing"
            }
            Recalls.insert {
                it[sku] = "pump"
                it[reason] = "faulty seal"
            }
        }

        transaction(database) {
            val eastSkus = EastStock.select(EastStock.sku)
            val westSkus = WestStock.select(WestStock.sku)

            val assortment: List<String> = eastSkus.union(westSkus).map { it[EastStock.sku] }.sorted()
            assertThat(assortment).containsExactly("bolt", "gear", "hose", "nut", "pump", "valve")

            assertThat(EastStock.select(EastStock.sku).unionAll(WestStock.select(WestStock.sku)).count())
                .isEqualTo(8L)

            val sharedSkus: List<String> = EastStock.select(EastStock.sku)
                .intersect(WestStock.select(WestStock.sku))
                .map { it[EastStock.sku] }
                .sorted()
            assertThat(sharedSkus).containsExactly("gear", "nut")

            val eastOnlySkus: List<String> = EastStock.select(EastStock.sku)
                .except(WestStock.select(WestStock.sku))
                .map { it[EastStock.sku] }
                .sorted()
            assertThat(eastOnlySkus).containsExactly("bolt", "valve")
        }

        transaction(database) {
            val copied: Int? = NetworkStock.insert(
                EastStock.select(EastStock.sku, EastStock.quantity),
                columns = listOf(NetworkStock.sku, NetworkStock.quantity),
            )
            assertThat(copied).isEqualTo(4)

            NetworkStock.mergeFrom(WestStock, on = { NetworkStock.sku eq WestStock.sku }) {
                whenMatchedUpdate {
                    it[NetworkStock.quantity] = NetworkStock.quantity + WestStock.quantity
                }
                whenNotMatchedInsert {
                    it[NetworkStock.sku] = WestStock.sku
                    it[NetworkStock.quantity] = WestStock.quantity
                }
            }

            val recalled = Recalls.select(Recalls.sku).alias("recalled")
            NetworkStock.mergeFrom(recalled, on = { NetworkStock.sku eq recalled[Recalls.sku] }) {
                whenMatchedDelete()
            }
        }

        transaction(database) {
            val network: List<Pair<String, Int>> = NetworkStock.selectAll()
                .orderBy(NetworkStock.sku to SortOrder.ASC)
                .map { it[NetworkStock.sku] to it[NetworkStock.quantity] }
            assertThat(network).containsExactly("bolt" to 120, "gear" to 40, "hose" to 60, "nut" to 380)

            val sellable: List<String> = NetworkStock.select(NetworkStock.sku)
                .except(Recalls.select(Recalls.sku))
                .map { it[NetworkStock.sku] }
                .sorted()
            assertThat(sellable).containsExactly("bolt", "gear", "hose", "nut")

            val plan: String = explain {
                NetworkStock.selectAll().where { NetworkStock.quantity greater 50 }
            }.joinToString("\n") { it.toString() }
            assertThat(plan).containsIgnoringCase("network_stock")
        }
    }
}
