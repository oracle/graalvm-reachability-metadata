/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_exposed.exposed_jdbc

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.LongColumnType
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.castTo
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.countDistinct
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.core.times
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test

/**
 * DSL joins and aggregate queries: a sales dashboard joins customers, orders, and order lines
 * through foreign keys and reports revenue per region with grouping, `having`, and ordering.
 */
class SalesReportJoinAggregationTest {

    object Customers : Table("customers") {
        val id = integer("id").autoIncrement()
        val name = varchar("name", 64)
        val region = varchar("region", 16)

        override val primaryKey = PrimaryKey(id)
    }

    object Orders : Table("orders") {
        val id = integer("id").autoIncrement()
        val customer = reference("customer_id", Customers.id, onDelete = ReferenceOption.CASCADE)
        val status = varchar("status", 16)

        override val primaryKey = PrimaryKey(id)
    }

    object OrderLines : Table("order_lines") {
        val order = reference("order_id", Orders.id, onDelete = ReferenceOption.CASCADE)
        val sku = varchar("sku", 32)
        val quantity = integer("quantity")
        val unitPriceCents = long("unit_price_cents")

        override val primaryKey = PrimaryKey(order, sku)
    }

    data class RegionRevenue(val region: String, val orders: Long, val revenueCents: Long)

    @Test
    fun reportsRevenuePerRegionFromJoinedTables() {
        val database = Database.connect(
            url = "jdbc:h2:mem:sales_report;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )

        transaction(database) {
            SchemaUtils.create(Customers, Orders, OrderLines)

            fun customer(name: String, region: String): Int =
                Customers.insert {
                    it[Customers.name] = name
                    it[Customers.region] = region
                } get Customers.id

            fun order(customerId: Int, status: String, vararg lines: Triple<String, Int, Long>) {
                val orderId = Orders.insert {
                    it[customer] = customerId
                    it[Orders.status] = status
                } get Orders.id
                lines.forEach { (sku, quantity, price) ->
                    OrderLines.insert {
                        it[order] = orderId
                        it[OrderLines.sku] = sku
                        it[OrderLines.quantity] = quantity
                        it[unitPriceCents] = price
                    }
                }
            }

            val ada = customer("Ada", "EU")
            val linus = customer("Linus", "EU")
            val grace = customer("Grace", "US")
            val ken = customer("Ken", "APAC")

            order(ada, "PAID", Triple("BOOK", 2, 1_500L), Triple("PEN", 3, 200L))
            order(linus, "PAID", Triple("LAPTOP", 1, 120_000L))
            order(linus, "CANCELLED", Triple("MOUSE", 5, 2_000L))
            order(grace, "PAID", Triple("BOOK", 1, 1_500L))
            order(grace, "PAID", Triple("DESK", 1, 30_000L), Triple("LAMP", 2, 4_000L))
            order(ken, "PAID", Triple("PEN", 1, 200L))
        }

        val lineTotal = OrderLines.quantity.castTo(LongColumnType()) * OrderLines.unitPriceCents
        val revenue = lineTotal.sum()
        val orderCount = Orders.id.countDistinct()

        val report: List<RegionRevenue> = transaction(database) {
            Customers
                .innerJoin(Orders)
                .innerJoin(OrderLines)
                .select(Customers.region, orderCount, revenue)
                .where { Orders.status eq "PAID" }
                .groupBy(Customers.region)
                .having { revenue greater 1_000L }
                .orderBy(revenue, SortOrder.DESC)
                .map { RegionRevenue(it[Customers.region], it[orderCount], it[revenue]!!) }
        }

        assertThat(report).containsExactly(
            RegionRevenue("EU", 2, 3_600L + 120_000L),
            RegionRevenue("US", 2, 1_500L + 38_000L),
        )

        val skuPopularity: List<Pair<String, Long>> = transaction(database) {
            val lines = OrderLines.sku.count()
            OrderLines
                .select(OrderLines.sku, lines)
                .groupBy(OrderLines.sku)
                .orderBy(lines to SortOrder.DESC, OrderLines.sku to SortOrder.ASC)
                .limit(2)
                .map { it[OrderLines.sku] to it[lines] }
        }
        assertThat(skuPopularity).containsExactly("BOOK" to 2L, "PEN" to 2L)

        val remainingLines: Long = transaction(database) {
            Customers.deleteWhere { Customers.name eq "Linus" }
            OrderLines.selectAll().count()
        }
        assertThat(remainingLines).isEqualTo(6)

        transaction(database) { SchemaUtils.drop(OrderLines, Orders, Customers) }
    }
}
