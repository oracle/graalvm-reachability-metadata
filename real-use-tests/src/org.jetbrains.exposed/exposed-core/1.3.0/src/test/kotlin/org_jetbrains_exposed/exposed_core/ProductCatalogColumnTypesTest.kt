/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_exposed.exposed_core

import java.math.BigDecimal
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Test

/**
 * Column type mapping: a product catalog stores enums by name and by ordinal, decimals,
 * booleans, UUIDs, binary data, nullable and defaulted columns, and an application-defined
 * `ColumnType`, then reads every value back as its Kotlin type.
 */
class ProductCatalogColumnTypesTest {

    enum class Category { HARDWARE, TOOLS, GARDEN }

    enum class Priority { LOW, NORMAL, HIGH }

    /** Stores a set of tags as one comma-separated `VARCHAR`, sorted for a stable encoding. */
    class TagsColumnType : ColumnType<Set<String>>() {
        override fun sqlType(): String = "VARCHAR(255)"

        override fun valueFromDB(value: Any): Set<String> = when (value) {
            is String -> if (value.isEmpty()) emptySet() else value.split(',').toSortedSet()
            else -> error("Unexpected tags value: $value")
        }

        override fun notNullValueToDB(value: Set<String>): Any = value.sorted().joinToString(",")
    }

    object Products : Table("products") {
        val sku: Column<UUID> = javaUUID("sku")
        val name: Column<String> = varchar("name", 64)
        val category: Column<Category> = enumerationByName<Category>("category", 16)
        val priority: Column<Priority> = enumeration<Priority>("priority").default(Priority.NORMAL)
        val price: Column<BigDecimal> = decimal("price", 10, 2)
        val inStock: Column<Boolean> = bool("in_stock").default(true)
        val thumbnail: Column<ByteArray> = binary("thumbnail", 16)
        val description: Column<String?> = text("description").nullable()
        val tags: Column<Set<String>> = registerColumn("tags", TagsColumnType())

        override val primaryKey = PrimaryKey(sku)
    }

    data class Product(
        val sku: UUID,
        val name: String,
        val category: Category,
        val priority: Priority,
        val price: BigDecimal,
        val inStock: Boolean,
        val thumbnail: List<Byte>,
        val description: String?,
        val tags: Set<String>,
    )

    private fun ResultRow.toProduct(): Product = Product(
        sku = this[Products.sku],
        name = this[Products.name],
        category = this[Products.category],
        priority = this[Products.priority],
        price = this[Products.price],
        inStock = this[Products.inStock],
        thumbnail = this[Products.thumbnail].toList(),
        description = this[Products.description],
        tags = this[Products.tags],
    )

    @Test
    fun storesAndReadsBackEveryColumnTypeOfTheCatalog() {
        val database: Database = Database.connect(
            url = "jdbc:h2:mem:product_catalog;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        val hammerSku: UUID = UUID.fromString("3f1c6c1e-6b1a-4c55-9a59-0c7d4b1a0001")
        val shovelSku: UUID = UUID.fromString("3f1c6c1e-6b1a-4c55-9a59-0c7d4b1a0002")
        val boltsSku: UUID = UUID.fromString("3f1c6c1e-6b1a-4c55-9a59-0c7d4b1a0003")

        val catalog: List<Product> = transaction(database) {
            SchemaUtils.create(Products)
            Products.insert {
                it[sku] = hammerSku
                it[name] = "Claw hammer"
                it[category] = Category.TOOLS
                it[priority] = Priority.HIGH
                it[price] = BigDecimal("24.90")
                it[thumbnail] = byteArrayOf(0x48, 0x4D)
                it[description] = "Forged steel head"
                it[tags] = setOf("steel", "bestseller")
            }
            Products.insert {
                it[sku] = shovelSku
                it[name] = "Garden shovel"
                it[category] = Category.GARDEN
                it[price] = BigDecimal("31.5")
                it[inStock] = false
                it[thumbnail] = byteArrayOf(0x53)
                it[tags] = emptySet()
            }
            Products.insert {
                it[sku] = boltsSku
                it[name] = "Hex bolts M8"
                it[category] = Category.HARDWARE
                it[priority] = Priority.LOW
                it[price] = BigDecimal("0.15")
                it[thumbnail] = byteArrayOf()
                it[description] = null
                it[tags] = setOf("zinc", "bulk", "m8")
            }

            Products.selectAll().orderBy(Products.name to SortOrder.ASC).map { it.toProduct() }
        }

        assertThat(catalog).containsExactly(
            Product(
                hammerSku, "Claw hammer", Category.TOOLS, Priority.HIGH, BigDecimal("24.90"),
                true, listOf<Byte>(0x48, 0x4D), "Forged steel head", setOf("bestseller", "steel"),
            ),
            Product(
                shovelSku, "Garden shovel", Category.GARDEN, Priority.NORMAL, BigDecimal("31.50"),
                false, listOf<Byte>(0x53), null, emptySet(),
            ),
            Product(
                boltsSku, "Hex bolts M8", Category.HARDWARE, Priority.LOW, BigDecimal("0.15"),
                true, emptyList(), null, setOf("bulk", "m8", "zinc"),
            ),
        )

        val restocked: List<String> = transaction(database) {
            val updated: Int = Products.update({
                (Products.category inList listOf(Category.GARDEN, Category.HARDWARE)) and
                    Products.description.isNull()
            }) {
                it[inStock] = true
                it[priority] = Priority.HIGH
                it[description] = "Restocked"
                it[tags] = setOf("restocked")
            }
            assertThat(updated).isEqualTo(2)

            Products.selectAll()
                .where { (Products.priority eq Priority.HIGH) and (Products.inStock eq true) }
                .orderBy(Products.price to SortOrder.DESC)
                .map { row -> "${row[Products.name]}|${row[Products.description]}|${row[Products.tags]}" }
        }
        assertThat(restocked).containsExactly(
            "Garden shovel|Restocked|[restocked]",
            "Claw hammer|Forged steel head|[bestseller, steel]",
            "Hex bolts M8|Restocked|[restocked]",
        )

        transaction(database) {
            SchemaUtils.drop(Products)
        }
    }
}
