/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_exposed.exposed_core

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.ExpressionWithColumnTypeAlias
import org.jetbrains.exposed.v1.core.LikePattern
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.charLength
import org.jetbrains.exposed.v1.core.concat
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.groupConcat
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.locate
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.notLike
import org.jetbrains.exposed.v1.core.regexp
import org.jetbrains.exposed.v1.core.stringLiteral
import org.jetbrains.exposed.v1.core.substring
import org.jetbrains.exposed.v1.core.trim
import org.jetbrains.exposed.v1.core.upperCase
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Test

/**
 * String functions and text pattern matching: a contact directory cleans imported e-mail
 * addresses, searches people with case-insensitive, escaped and negated `LIKE` patterns and a
 * regular expression, and renders display names, initials and per-city name lists in SQL.
 */
class ContactDirectoryTextSearchTest {

    object Contacts : Table("contacts") {
        val id = integer("id")
        val firstName = varchar("first_name", 40)
        val lastName = varchar("last_name", 40)
        val email = varchar("email", 80)
        val city = varchar("city", 40)
        val promoCode = varchar("promo_code", 20)

        override val primaryKey = PrimaryKey(id)
    }

    data class ImportedContact(
        val id: Int,
        val firstName: String,
        val lastName: String,
        val email: String,
        val city: String,
        val promoCode: String,
    )

    @Test
    fun cleansSearchesAndRendersContactsWithSqlStringFunctions() {
        val database: Database = Database.connect(
            url = "jdbc:h2:mem:contact_directory;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        val imported: List<ImportedContact> = listOf(
            ImportedContact(1, "Ada", "Smith", "  Ada.Smith@Example.com ", "London", "SAVE10%"),
            ImportedContact(2, "Brian", "smithers", "brian.smithers@example.com", "Leeds", "SAVE_10"),
            ImportedContact(3, "Clara", "Jones", " clara@partner.org", "London", "WELCOME"),
            ImportedContact(4, "Dmitri", "Smythe", "DMITRI.SMYTHE@EXAMPLE.COM  ", "London", "SAVE105"),
        )
        transaction(database) {
            SchemaUtils.create(Contacts)
            Contacts.batchInsert(imported) { contact ->
                this[Contacts.id] = contact.id
                this[Contacts.firstName] = contact.firstName
                this[Contacts.lastName] = contact.lastName
                this[Contacts.email] = contact.email
                this[Contacts.city] = contact.city
                this[Contacts.promoCode] = contact.promoCode
            }
        }

        transaction(database) {
            // Imported addresses are normalised in place by the database.
            val cleaned: Int = Contacts.update {
                it[email] = email.trim().lowerCase()
            }
            assertThat(cleaned).isEqualTo(4)
            assertThat(Contacts.select(Contacts.email).orderBy(Contacts.id).map { it[Contacts.email] })
                .containsExactly(
                    "ada.smith@example.com",
                    "brian.smithers@example.com",
                    "clara@partner.org",
                    "dmitri.smythe@example.com",
                )
        }

        transaction(database) {
            // Case-insensitive prefix search on the surname.
            val smiths: List<Int> = Contacts.select(Contacts.id)
                .where { Contacts.lastName.lowerCase() like "smi%" }
                .orderBy(Contacts.id)
                .map { it[Contacts.id] }
            assertThat(smiths).containsExactly(1, 2)

            // Promo codes containing a literal "10%" must not treat '%' as a wildcard.
            val literalPercent: LikePattern = LikePattern("%", '\\') + LikePattern.ofLiteral("10%")
            val percentCodes: List<String> = Contacts.select(Contacts.promoCode)
                .where { Contacts.promoCode like literalPercent }
                .map { it[Contacts.promoCode] }
            assertThat(percentCodes).containsExactly("SAVE10%")

            // A literal '_' only matches an underscore, not any single character.
            val underscoreCodes: List<String> = Contacts.select(Contacts.promoCode)
                .where { Contacts.promoCode like (LikePattern.ofLiteral("SAVE_") + "%") }
                .map { it[Contacts.promoCode] }
            assertThat(underscoreCodes).containsExactly("SAVE_10")

            // Partner addresses are excluded from the newsletter audience.
            val newsletter: List<Int> = Contacts.select(Contacts.id)
                .where { (Contacts.city eq "London") and (Contacts.email notLike "%@partner.org") }
                .orderBy(Contacts.id)
                .map { it[Contacts.id] }
            assertThat(newsletter).containsExactly(1, 4)

            // Only "first.last@example.com" addresses follow the corporate convention.
            val corporate: List<Int> = Contacts.select(Contacts.id)
                .where { Contacts.email regexp "^[a-z]+\\.[a-z]+@example\\.com$" }
                .orderBy(Contacts.id)
                .map { it[Contacts.id] }
            assertThat(corporate).containsExactly(1, 2, 4)
        }

        transaction(database) {
            val displayName: ExpressionWithColumnTypeAlias<String> =
                concat(Contacts.firstName, stringLiteral(" "), Contacts.lastName.upperCase())
                    .alias("display_name")
            val initial: ExpressionWithColumnTypeAlias<String> =
                Contacts.firstName.substring(1, 1).alias("initial")
            val nameLength: ExpressionWithColumnTypeAlias<Int?> =
                Contacts.lastName.charLength().alias("name_length")
            val atPosition: ExpressionWithColumnTypeAlias<Int> =
                Contacts.email.locate("@").alias("at_position")

            val cards: List<String> = Contacts
                .select(displayName, initial, nameLength, atPosition)
                .orderBy(Contacts.id)
                .map { row -> "${row[displayName]}|${row[initial]}|${row[nameLength]}|${row[atPosition]}" }
            assertThat(cards).containsExactly(
                "Ada SMITH|A|5|10",
                "Brian SMITHERS|B|8|15",
                "Clara JONES|C|5|6",
                "Dmitri SMYTHE|D|6|14",
            )

            val residents: ExpressionWithColumnTypeAlias<String> = Contacts.firstName
                .groupConcat(separator = ", ", orderBy = Contacts.firstName to SortOrder.ASC)
                .alias("residents")
            val byCity: Map<String, String> = Contacts
                .select(Contacts.city, residents)
                .groupBy(Contacts.city)
                .associate { it[Contacts.city] to it[residents] }
            assertThat(byCity).containsExactlyInAnyOrderEntriesOf(
                mapOf("London" to "Ada, Clara, Dmitri", "Leeds" to "Brian"),
            )
        }
    }
}
