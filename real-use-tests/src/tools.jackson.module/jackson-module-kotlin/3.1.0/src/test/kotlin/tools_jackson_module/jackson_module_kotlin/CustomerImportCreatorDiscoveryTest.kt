/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools_jackson_module.jackson_module_kotlin

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

/**
 * Creator discovery beyond the primary constructor (companion-object factories, annotated
 * secondary constructors, private constructors) and Kotlin property naming, as a CRM service
 * imports customer records from a partner feed.
 */
public class CustomerImportCreatorDiscoveryTest {

    /** Normalizes partner SKUs through a companion factory; the constructor stays private. */
    class Sku private constructor(val code: String, val family: String) {
        companion object {
            @JvmStatic
            @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
            fun of(code: String): Sku {
                val normalized = code.trim().uppercase()
                return Sku(normalized, normalized.substringBefore('-'))
            }
        }

        override fun equals(other: Any?): Boolean = other is Sku && other.code == code

        override fun hashCode(): Int = code.hashCode()
    }

    /** Travels as a single string such as `"EUR 12.50"` in both directions. */
    data class Price(val currency: String, val minorUnits: Long) {
        @JsonValue
        fun asText(): String {
            val cents: String = (minorUnits % 100).toString().padStart(2, '0')
            return "$currency ${minorUnits / 100}.$cents"
        }

        companion object {
            @JvmStatic
            @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
            fun parse(text: String): Price {
                val (currency, amount) = text.split(' ')
                val (major, minor) = amount.split('.')
                return Price(currency, major.toLong() * 100 + minor.toLong())
            }
        }
    }

    /** The feed sends first and last name; the CRM stores one display name. */
    class Customer(val id: String, val displayName: String, val tier: Int) {
        @JsonCreator
        constructor(id: String, firstName: String, lastName: String, tier: Int?) :
            this(id, "$firstName $lastName", tier ?: 1)
    }

    data class Preferences(
        val isSubscribed: Boolean,
        val hasPaperInvoices: Boolean,
        @param:JsonProperty("postal_code") val postalCode: String,
        @JsonIgnore val sessionToken: String = "none",
    ) {
        val region: String
            get() = postalCode.take(2)
    }

    data class ImportRecord(
        val customer: Customer,
        val preferences: Preferences,
        val basket: Map<String, Price>,
        val favourites: List<Sku>,
    )

    private val mapper = jacksonObjectMapper()

    @Test
    fun bindsAPartnerRecordThroughFactoriesAndSecondaryConstructors(): Unit {
        val feedLine = """
            {
              "customer": {"id": "c-17", "firstName": "Ada", "lastName": "Lovelace", "tier": null},
              "preferences": {
                "isSubscribed": true,
                "hasPaperInvoices": false,
                "postal_code": "75008",
                "sessionToken": "leaked"
              },
              "basket": {" tool-12 ": "EUR 12.50", "garden-3": "EUR 0.05"},
              "favourites": ["tool-12", " garden-3"]
            }
        """.trimIndent()

        val record: ImportRecord = mapper.readValue(feedLine)

        assertThat(record.customer.id).isEqualTo("c-17")
        assertThat(record.customer.displayName).isEqualTo("Ada Lovelace")
        assertThat(record.customer.tier).isEqualTo(1)
        assertThat(record.preferences.isSubscribed).isTrue()
        assertThat(record.preferences.hasPaperInvoices).isFalse()
        assertThat(record.preferences.postalCode).isEqualTo("75008")
        assertThat(record.preferences.sessionToken).isEqualTo("none")
        assertThat(record.basket).containsEntry(" tool-12 ", Price("EUR", 1_250))
        assertThat(record.basket).containsEntry("garden-3", Price("EUR", 5))
        assertThat(record.favourites.map { it.code }).containsExactly("TOOL-12", "GARDEN-3")
        assertThat(record.favourites.map { it.family }).containsExactly("TOOL", "GARDEN")
    }

    @Test
    fun exportsKotlinPropertyNamesAndJsonValueForms(): Unit {
        val record = ImportRecord(
            customer = Customer(id = "c-2", displayName = "Grace Hopper", tier = 3),
            preferences = Preferences(
                isSubscribed = false,
                hasPaperInvoices = true,
                postalCode = "10115",
                sessionToken = "secret",
            ),
            basket = linkedMapOf("book" to Price("USD", 4_999)),
            favourites = listOf(Sku.of("book-1")),
        )

        val tree: JsonNode = mapper.readTree(mapper.writeValueAsString(record))

        val customer: JsonNode = tree.get("customer")
        assertThat(customer.propertyNames()).containsExactlyInAnyOrder("id", "displayName", "tier")
        assertThat(customer.get("displayName").asString()).isEqualTo("Grace Hopper")
        val preferences: JsonNode = tree.get("preferences")
        assertThat(preferences.propertyNames())
            .containsExactlyInAnyOrder("isSubscribed", "hasPaperInvoices", "postal_code", "region")
        assertThat(preferences.get("isSubscribed").booleanValue()).isFalse()
        assertThat(preferences.get("hasPaperInvoices").booleanValue()).isTrue()
        assertThat(preferences.get("region").asString()).isEqualTo("10")
        assertThat(tree.get("basket").get("book").asString()).isEqualTo("USD 49.99")
        assertThat(tree.get("favourites").get(0).get("code").asString()).isEqualTo("BOOK-1")
        assertThat(tree.get("favourites").get(0).get("family").asString()).isEqualTo("BOOK")
    }

    @Test
    fun readsTopLevelValuesThroughTheirCompanionFactories(): Unit {
        val prices: List<Price> = mapper.readValue("""["GBP 1.00", "JPY 300.00"]""")
        val sku: Sku = mapper.readValue("\" lamp-9 \"")

        assertThat(prices).containsExactly(Price("GBP", 100), Price("JPY", 30_000))
        assertThat(sku.code).isEqualTo("LAMP-9")
        assertThat(sku).isEqualTo(Sku.of("lamp-9"))
        assertThat(mapper.writeValueAsString(prices)).isEqualTo("""["GBP 1.00","JPY 300.00"]""")
    }
}
