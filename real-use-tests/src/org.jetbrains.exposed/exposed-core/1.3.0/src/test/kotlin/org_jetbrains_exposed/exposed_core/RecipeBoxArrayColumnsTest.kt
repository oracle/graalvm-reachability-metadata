/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_exposed.exposed_core

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.VarCharColumnType
import org.jetbrains.exposed.v1.core.allFrom
import org.jetbrains.exposed.v1.core.anyFrom
import org.jetbrains.exposed.v1.core.arrayParam
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.get
import org.jetbrains.exposed.v1.core.intParam
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.slice
import org.jetbrains.exposed.v1.core.stringParam
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Test

/**
 * Array column types and array operators: a recipe box stores tags and ratings in SQL array
 * columns, reads elements and slices back, filters with `= ANY(...)` and `<= ALL(...)`, and
 * replaces whole arrays through bound array parameters.
 */
class RecipeBoxArrayColumnsTest {

    object Recipes : Table("recipes") {
        val id = integer("id")
        val name = varchar("name", 64)
        val tags = array<String>("tags", VarCharColumnType(32), 5)
        val ratings = array<Int>("ratings")
        val allergens = array<String>("allergens").default(emptyList())

        override val primaryKey = PrimaryKey(id)
    }

    data class Recipe(val id: Int, val name: String, val tags: List<String>, val ratings: List<Int>)

    data class Card(val name: String, val tags: List<String>, val ratings: List<Int>, val allergens: List<String>)

    private fun ResultRow.toCard(): Card =
        Card(this[Recipes.name], this[Recipes.tags], this[Recipes.ratings], this[Recipes.allergens])

    @Test
    fun storesQueriesAndUpdatesRecipesThroughArrayColumns() {
        val database: Database = Database.connect(
            url = "jdbc:h2:mem:recipe_box;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        val recipes: List<Recipe> = listOf(
            Recipe(1, "Lentil soup", listOf("vegan", "soup", "winter"), listOf(5, 4, 5)),
            Recipe(2, "Carbonara", listOf("pasta", "quick"), listOf(4, 2, 5)),
            Recipe(3, "Falafel wrap", listOf("vegan", "street", "quick"), listOf(3, 4)),
        )
        transaction(database) {
            SchemaUtils.create(Recipes)
            Recipes.batchInsert(recipes) { recipe ->
                this[Recipes.id] = recipe.id
                this[Recipes.name] = recipe.name
                this[Recipes.tags] = recipe.tags
                this[Recipes.ratings] = recipe.ratings
            }
            Recipes.insert {
                it[id] = 4
                it[name] = "Peanut noodles"
                it[tags] = listOf("vegan", "noodles")
                it[ratings] = listOf(4, 4, 3, 5)
                it[allergens] = listOf("peanut", "soy")
            }
        }

        val cards: List<Card> = transaction(database) {
            Recipes.selectAll().orderBy(Recipes.id).map { it.toCard() }
        }
        assertThat(cards).containsExactly(
            Card("Lentil soup", listOf("vegan", "soup", "winter"), listOf(5, 4, 5), emptyList()),
            Card("Carbonara", listOf("pasta", "quick"), listOf(4, 2, 5), emptyList()),
            Card("Falafel wrap", listOf("vegan", "street", "quick"), listOf(3, 4), emptyList()),
            Card("Peanut noodles", listOf("vegan", "noodles"), listOf(4, 4, 3, 5), listOf("peanut", "soy")),
        )

        val primaryTagAndFirstTwoRatings: Map<String, Pair<String?, List<Int>?>> = transaction(database) {
            val primaryTag = Recipes.tags[1]
            val firstTwoRatings = Recipes.ratings.slice(1, 2)
            Recipes.select(Recipes.name, primaryTag, firstTwoRatings)
                .orderBy(Recipes.id)
                .associate { row -> row[Recipes.name] to (row[primaryTag] to row[firstTwoRatings]) }
        }
        assertThat(primaryTagAndFirstTwoRatings).containsExactly(
            entry("Lentil soup", "vegan" to listOf(5, 4)),
            entry("Carbonara", "pasta" to listOf(4, 2)),
            entry("Falafel wrap", "vegan" to listOf(3, 4)),
            entry("Peanut noodles", "vegan" to listOf(4, 4)),
        )

        val quickRecipes: List<String> = transaction(database) {
            Recipes.select(Recipes.name)
                .where { stringParam("quick") eq anyFrom(Recipes.tags) }
                .orderBy(Recipes.name, SortOrder.ASC)
                .map { it[Recipes.name] }
        }
        assertThat(quickRecipes).containsExactly("Carbonara", "Falafel wrap")

        val consistentlyGood: List<String> = transaction(database) {
            Recipes.select(Recipes.name)
                .where { intParam(3) lessEq allFrom(Recipes.ratings) }
                .orderBy(Recipes.id)
                .map { it[Recipes.name] }
        }
        assertThat(consistentlyGood).containsExactly("Lentil soup", "Falafel wrap", "Peanut noodles")

        val pickedById: List<String> = transaction(database) {
            Recipes.select(Recipes.name)
                .where { Recipes.id eq anyFrom(listOf(2, 4)) }
                .orderBy(Recipes.id)
                .map { it[Recipes.name] }
        }
        assertThat(pickedById).containsExactly("Carbonara", "Peanut noodles")

        val updatedRows: Int = transaction(database) {
            Recipes.update({ Recipes.tags eq arrayParam(listOf("pasta", "quick")) }) {
                it[tags] = listOf("pasta", "quick", "classic")
                it[allergens] = listOf("egg", "gluten")
            }
        }
        assertThat(updatedRows).isEqualTo(1)

        val carbonara: Card = transaction(database) {
            Recipes.selectAll().where { Recipes.id eq 2 }.single().toCard()
        }
        assertThat(carbonara).isEqualTo(
            Card("Carbonara", listOf("pasta", "quick", "classic"), listOf(4, 2, 5), listOf("egg", "gluten")),
        )

        val allergenFree: List<String> = transaction(database) {
            Recipes.select(Recipes.name)
                .where { Recipes.allergens eq arrayParam(emptyList<String>()) }
                .orderBy(Recipes.id)
                .map { it[Recipes.name] }
        }
        assertThat(allergenFree).containsExactly("Lentil soup", "Falafel wrap")

        transaction(database) {
            SchemaUtils.drop(Recipes)
        }
    }
}
