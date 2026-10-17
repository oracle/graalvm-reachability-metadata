/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_exposed.exposed_jdbc

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.allFrom
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.anyFrom
import org.jetbrains.exposed.v1.core.charLength
import org.jetbrains.exposed.v1.core.coalesce
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.concat
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.get
import org.jetbrains.exposed.v1.core.intLiteral
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.locate
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.regexp
import org.jetbrains.exposed.v1.core.slice
import org.jetbrains.exposed.v1.core.stringLiteral
import org.jetbrains.exposed.v1.core.substring
import org.jetbrains.exposed.v1.core.trim
import org.jetbrains.exposed.v1.core.upperCase
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.Query
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
 * PostgreSQL array columns and SQL expression building: a grocery storefront keeps product tags and
 * review ratings in `array` columns, searches them with `anyFrom`/`allFrom`, element access and
 * slices, composes optional shopper filters with `compoundAnd` and `adjustWhere`, formats labels
 * with string functions, `coalesce` and a `CustomFunction`, and pages results with `withDistinctOn`,
 * `limit` and `offset`.
 */
class GroceryCatalogArraySearchTest {

    object Products : Table("products") {
        val id = integer("id").autoIncrement()
        val sku = varchar("sku", 32).uniqueIndex()
        val name = varchar("name", 100)
        val category = varchar("category", 32)
        val displayName = varchar("display_name", 100).nullable()
        val priceCents = integer("price_cents")
        val tags: Column<List<String>> = array<String>("tags")
        val ratings: Column<List<Int>> = array<Int>("ratings")

        override val primaryKey = PrimaryKey(id)
    }

    data class Product(
        val sku: String,
        val name: String,
        val category: String,
        val displayName: String?,
        val priceCents: Int,
        val tags: List<String>,
        val ratings: List<Int>,
    )

    /** The optional filters a shopper fills in on the search page; blank ones are left out. */
    data class ShopperSearch(val nameFragment: String? = null, val tag: String? = null, val maxPriceCents: Int? = null) {
        fun toQuery(): Query {
            val filters: List<Op<Boolean>> = listOfNotNull(
                nameFragment?.let { Products.name.lowerCase() like "%${it.lowercase()}%" },
                tag?.let { stringLiteral(it) eq anyFrom(Products.tags) },
                maxPriceCents?.let { Products.priceCents lessEq it },
            )
            val query: Query = Products.selectAll().orderBy(Products.sku)
            return if (filters.isEmpty()) query else query.where(filters.compoundAnd())
        }
    }

    companion object {
        private const val PASSWORD = "grocery-secret"
        private val containerName: String = "real-use-exposed-jdbc-grocery-" + UUID.randomUUID()
        private var port: Int = 0

        private val CATALOG: List<Product> = listOf(
            Product("BAK-001", "sourdough loaf", "bakery", null, 650, listOf("vegan", "fresh"), listOf(5, 4, 5)),
            Product("BAK-002", "butter croissant", "bakery", "Croissant", 320, listOf("fresh"), listOf(4, 2)),
            Product("DRK-001", "green tea", "drinks", null, 480, listOf("vegan", "organic", "caffeine"), listOf(4, 4)),
            Product("DRK-002", "oat milk latte", "drinks", "Oat Latte", 550, listOf("vegan", "caffeine"), listOf(3, 5, 4)),
            Product("DRK-003", "sparkling water", "drinks", null, 150, listOf("vegan"), listOf(3)),
            Product("FRU-001", "  fuji apples  ", "fruit", "Fuji Apples", 399, listOf("organic", "fresh"), listOf(5, 5)),
        )

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
    fun searchesArrayColumnsAndFormatsCatalogWithSqlFunctions() {
        val database: Database = Database.connect(
            url = jdbcUrl,
            driver = "org.postgresql.Driver",
            user = "postgres",
            password = PASSWORD,
        )

        transaction(database) {
            SchemaUtils.create(Products)
            Products.batchInsert(CATALOG) { product ->
                this[Products.sku] = product.sku
                this[Products.name] = product.name
                this[Products.category] = product.category
                this[Products.displayName] = product.displayName
                this[Products.priceCents] = product.priceCents
                this[Products.tags] = product.tags
                this[Products.ratings] = product.ratings
            }
        }

        transaction(database) {
            val stored: Map<String, Pair<List<String>, List<Int>>> = Products.selectAll()
                .associate { it[Products.sku] to (it[Products.tags] to it[Products.ratings]) }
            assertThat(stored).hasSize(CATALOG.size)
            assertThat(stored.getValue("DRK-001")).isEqualTo(listOf("vegan", "organic", "caffeine") to listOf(4, 4))

            val veganSkus: List<String> = Products.select(Products.sku)
                .where { stringLiteral("vegan") eq anyFrom(Products.tags) }
                .orderBy(Products.sku)
                .map { it[Products.sku] }
            assertThat(veganSkus).containsExactly("BAK-001", "DRK-001", "DRK-002", "DRK-003")

            val neverBelowFour: List<String> = Products.select(Products.sku)
                .where { intLiteral(4) lessEq allFrom(Products.ratings) }
                .orderBy(Products.sku)
                .map { it[Products.sku] }
            assertThat(neverBelowFour).containsExactly("BAK-001", "DRK-001", "FRU-001")

            val primaryTag = Products.tags[1]
            val firstTwoRatings = Products.ratings.slice(1, 2)
            val highlights: Map<String, Pair<String?, List<Int>>> = Products.select(Products.sku, primaryTag, firstTwoRatings)
                .where { Products.category eq "drinks" }
                .associate { it[Products.sku] to (it[primaryTag] to it[firstTwoRatings]) }
            assertThat(highlights).containsExactlyInAnyOrderEntriesOf(
                mapOf(
                    "DRK-001" to ("vegan" to listOf(4, 4)),
                    "DRK-002" to ("vegan" to listOf(3, 5)),
                    "DRK-003" to ("vegan" to listOf(3)),
                ),
            )
        }

        transaction(database) {
            Products.update({ Products.sku eq "BAK-002" }) {
                it[tags] = listOf("fresh", "vegan")
                it[ratings] = listOf(4, 2, 5)
            }
            val croissant = Products.selectAll().where { Products.sku eq "BAK-002" }.single()
            assertThat(croissant[Products.tags]).containsExactly("fresh", "vegan")
            assertThat(croissant[Products.ratings]).containsExactly(4, 2, 5)
        }

        transaction(database) {
            fun skus(search: ShopperSearch): List<String> = search.toQuery().map { it[Products.sku] }

            assertThat(skus(ShopperSearch())).hasSize(CATALOG.size)
            assertThat(skus(ShopperSearch(tag = "caffeine"))).containsExactly("DRK-001", "DRK-002")
            assertThat(skus(ShopperSearch(tag = "vegan", maxPriceCents = 500)))
                .containsExactly("BAK-002", "DRK-001", "DRK-003")
            assertThat(skus(ShopperSearch(nameFragment = "LATTE", tag = "vegan"))).containsExactly("DRK-002")

            val organicOnly: List<String> = ShopperSearch(maxPriceCents = 500).toQuery()
                .adjustWhere { checkNotNull(this) and (stringLiteral("organic") eq anyFrom(Products.tags)) }
                .map { it[Products.sku] }
            assertThat(organicOnly).containsExactly("DRK-001", "FRU-001")

            val skuCodedDrinks: List<String> = Products.select(Products.sku)
                .where { Products.sku regexp "^DRK-00[12]$" }
                .orderBy(Products.sku)
                .map { it[Products.sku] }
            assertThat(skuCodedDrinks).containsExactly("DRK-001", "DRK-002")
        }

        transaction(database) {
            val shelfName = coalesce(Products.displayName, CustomFunction("initcap", TextColumnType(), Products.name.trim()))
            val label = concat(" | ", listOf(Products.sku, Products.category.upperCase(), shelfName))
            val nameLength = Products.name.trim().charLength()
            val prefix = Products.category.substring(1, 3)
            val teaPosition = Products.name.locate("tea")
            val labels: Map<String, List<Any?>> = Products.select(Products.sku, label, nameLength, prefix, teaPosition)
                .orderBy(Products.sku)
                .associate { it[Products.sku] to listOf(it[label], it[nameLength], it[prefix], it[teaPosition]) }

            assertThat(labels.getValue("BAK-001")).containsExactly("BAK-001 | BAKERY | Sourdough Loaf", 14, "bak", 0)
            assertThat(labels.getValue("BAK-002")).containsExactly("BAK-002 | BAKERY | Croissant", 16, "bak", 0)
            assertThat(labels.getValue("DRK-001")).containsExactly("DRK-001 | DRINKS | Green Tea", 9, "dri", 7)
            assertThat(labels.getValue("FRU-001")).containsExactly("FRU-001 | FRUIT | Fuji Apples", 11, "fru", 0)
        }

        transaction(database) {
            val cheapestPerCategory: List<Pair<String, String>> = Products.select(Products.category, Products.sku)
                .withDistinctOn(Products.category to SortOrder.ASC)
                .orderBy(Products.category to SortOrder.ASC, Products.priceCents to SortOrder.ASC)
                .map { it[Products.category] to it[Products.sku] }
            assertThat(cheapestPerCategory).containsExactly("bakery" to "BAK-002", "drinks" to "DRK-003", "fruit" to "FRU-001")

            val byPrice: Query = Products.select(Products.sku).orderBy(Products.priceCents to SortOrder.DESC)
            val pages: List<List<String>> = (0L until 3L).map { page ->
                byPrice.copy().limit(2).offset(page * 2).map { it[Products.sku] }
            }
            assertThat(pages).containsExactly(
                listOf("BAK-001", "DRK-002"),
                listOf("DRK-001", "FRU-001"),
                listOf("BAK-002", "DRK-003"),
            )

            SchemaUtils.drop(Products)
        }
    }
}
