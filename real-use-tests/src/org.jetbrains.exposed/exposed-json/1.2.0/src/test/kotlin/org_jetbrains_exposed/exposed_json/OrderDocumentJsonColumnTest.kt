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
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.json.json
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Test

/**
 * Kotlinx-serialization json columns: an order service persists typed order documents in a
 * `json` column through the Exposed JDBC DSL and reads them back as Kotlin objects.
 */
class OrderDocumentJsonColumnTest {

    @Serializable
    data class LineItem(val sku: String, val quantity: Int, val unitPriceCents: Long)

    @Serializable
    data class ShippingAddress(val street: String, val city: String, val postalCode: String? = null)

    @Serializable
    data class OrderDocument(
        val customer: String,
        val items: List<LineItem>,
        val shipping: ShippingAddress,
        val notes: Map<String, String> = emptyMap(),
        val giftWrapped: Boolean = false,
    )

    object Orders : Table("orders") {
        val id = integer("id").autoIncrement()
        val reference = varchar("reference", 32).uniqueIndex()
        val document = json<OrderDocument>("document", Json { encodeDefaults = false })

        override val primaryKey = PrimaryKey(id)
    }

    @Test
    fun storesUpdatesAndReadsBackTypedOrderDocuments() {
        val database = Database.connect(
            url = "jdbc:h2:mem:order_documents;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        val first = OrderDocument(
            customer = "Ada Lovelace",
            items = listOf(LineItem("BOOK-1", 2, 1_999), LineItem("PEN-7", 1, 250)),
            shipping = ShippingAddress("12 Analytical Way", "London", "N1 9GU"),
            notes = mapOf("channel" to "web"),
        )
        val second = OrderDocument(
            customer = "Grace Hopper",
            items = listOf(LineItem("COBOL-GUIDE", 1, 4_500)),
            shipping = ShippingAddress("1 Compiler Road", "Arlington"),
            giftWrapped = true,
        )

        transaction(database) {
            SchemaUtils.create(Orders)
            Orders.insert {
                it[reference] = "ORD-1"
                it[document] = first
            }
            Orders.insert {
                it[reference] = "ORD-2"
                it[document] = second
            }
        }

        val stored: Map<String, OrderDocument> = transaction(database) {
            Orders.selectAll().associate { it[Orders.reference] to it[Orders.document] }
        }
        assertThat(stored).containsOnlyKeys("ORD-1", "ORD-2")
        assertThat(stored["ORD-1"]).isEqualTo(first)
        assertThat(stored["ORD-2"]).isEqualTo(second)
        assertThat(stored.getValue("ORD-1").items.sumOf { it.quantity * it.unitPriceCents }).isEqualTo(4_248)
        assertThat(stored.getValue("ORD-2").shipping.postalCode).isNull()

        val shipped = first.copy(notes = first.notes + ("status" to "shipped"))
        val updatedRows: Int = transaction(database) {
            Orders.update({ Orders.reference eq "ORD-1" }) { it[document] = shipped }
        }
        assertThat(updatedRows).isEqualTo(1)

        val reloaded: OrderDocument = transaction(database) {
            Orders.selectAll().where { Orders.reference eq "ORD-1" }.single()[Orders.document]
        }
        assertThat(reloaded.notes).containsEntry("channel", "web").containsEntry("status", "shipped")
        assertThat(reloaded.items).isEqualTo(first.items)

        transaction(database) { SchemaUtils.drop(Orders) }
    }
}
