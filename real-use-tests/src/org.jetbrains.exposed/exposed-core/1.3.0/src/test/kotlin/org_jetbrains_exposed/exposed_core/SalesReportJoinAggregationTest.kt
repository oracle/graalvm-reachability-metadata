/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_exposed.exposed_core

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.core.times
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test

/**
 * Core SQL DSL joins and aggregation: a sales dashboard joins customers to their orders and
 * computes per-region revenue with grouped sums, counts, `HAVING` filters and ordering.
 */
class SalesReportJoinAggregationTest {

    object Customers : Table("customers") {
        val id = integer("id")
        val name = varchar("name", 64)
        val region = varchar("region", 16)

        override val primaryKey = PrimaryKey(id)
    }

    object Orders : Table("orders") {
        val id = integer("id").autoIncrement()
        val customerId = integer("customer_id").references(Customers.id, onDelete = ReferenceOption.CASCADE)
        val quantity = long("quantity")
        val unitPriceCents = long("unit_price_cents")
        val status = varchar("status", 16)

        override val primaryKey = PrimaryKey(id)
    }

    data class Customer(val id: Int, val name: String, val region: String)

    data class Order(val customerId: Int, val quantity: Long, val unitPriceCents: Long, val status: String)

    data class RegionRevenue(val region: String, val orderCount: Long, val revenueCents: Long)

    @Test
    fun aggregatesShippedRevenuePerRegionAcrossJoinedTables() {
        val database: Database = Database.connect(
            url = "jdbc:h2:mem:sales_report;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        val customers: List<Customer> = listOf(
            Customer(1, "Northwind", "EU"),
            Customer(2, "Contoso", "US"),
            Customer(3, "Fabrikam", "US"),
            Customer(4, "Tailspin", "APAC"),
        )
        val orders: List<Order> = listOf(
            Order(1, 2, 1_500, "SHIPPED"),
            Order(1, 1, 4_000, "SHIPPED"),
            Order(2, 10, 300, "SHIPPED"),
            Order(3, 3, 2_000, "SHIPPED"),
            Order(3, 5, 9_999, "CANCELLED"),
            Order(4, 1, 700, "SHIPPED"),
        )

        val report: List<RegionRevenue> = transaction(database) {
            SchemaUtils.create(Customers, Orders)
            for (customer in customers) {
                Customers.insert {
                    it[id] = customer.id
                    it[name] = customer.name
                    it[region] = customer.region
                }
            }
            Orders.batchInsert(orders) { order ->
                this[Orders.customerId] = order.customerId
                this[Orders.quantity] = order.quantity
                this[Orders.unitPriceCents] = order.unitPriceCents
                this[Orders.status] = order.status
            }

            val lineTotal = Orders.quantity times Orders.unitPriceCents
            val revenue = lineTotal.sum()
            val orderCount = Orders.id.count()

            (Customers innerJoin Orders)
                .select(Customers.region, orderCount, revenue)
                .where { Orders.status eq "SHIPPED" }
                .groupBy(Customers.region)
                .having { revenue greater 1_000L }
                .orderBy(revenue to SortOrder.DESC)
                .map { row -> RegionRevenue(row[Customers.region], row[orderCount], row[revenue]!!) }
        }

        assertThat(report).containsExactly(
            RegionRevenue("US", 2, 9_000),
            RegionRevenue("EU", 2, 7_000),
        )

        val topBuyer: String = transaction(database) {
            (Customers innerJoin Orders)
                .select(Customers.name)
                .where { (Orders.status eq "SHIPPED") and (Customers.region eq "US") }
                .orderBy(Orders.quantity to SortOrder.DESC)
                .limit(1)
                .single()[Customers.name]
        }
        assertThat(topBuyer).isEqualTo("Contoso")

        transaction(database) {
            SchemaUtils.drop(Orders, Customers)
        }
    }
}
