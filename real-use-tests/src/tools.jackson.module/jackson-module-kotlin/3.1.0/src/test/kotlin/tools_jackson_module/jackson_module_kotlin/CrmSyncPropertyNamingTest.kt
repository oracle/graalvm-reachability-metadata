/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools_jackson_module.jackson_module_kotlin

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonUnwrapped
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.jsonMapper
import tools.jackson.module.kotlin.kotlinModule
import tools.jackson.module.kotlin.readValue

/**
 * Property naming and object shape of Kotlin classes: `@JsonNaming` and mapper-wide naming
 * strategies, `@JsonAlias` on creator parameters, `@JsonUnwrapped` prefixed flattening,
 * ignored unknown fields, inherited abstract and interface properties, and `by lazy`
 * derived properties, as a CRM sync service maps two partners' differently shaped feeds.
 */
public class CrmSyncPropertyNamingTest {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
    data class PostalAddress(val streetLine: String, val postalCode: String, val countryCode: String = "DE")

    /** A contact as the legacy CRM exports it: snake_case, flat address columns, old names. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class CrmContact(
        val contactId: Long,
        @JsonAlias("fullName", "display_name_v1")
        val displayName: String,
        val emailAddress: String,
        @get:JsonUnwrapped(prefix = "billing_")
        val billingAddress: PostalAddress,
        val marketingOptIn: Boolean = false,
    ) {
        /** Derived once per instance; exported so the reporting team can group by domain. */
        val emailDomain: String by lazy { emailAddress.substringAfter('@').lowercase() }
    }

    interface Versioned {
        val revision: Int
    }

    abstract class SyncRecord : Versioned {
        abstract val sourceSystem: String

        val schema: String
            get() = "crm-sync/$sourceSystem"
    }

    /** A webhook from the newer partner, whose whole API is kebab-case. */
    data class PartnerWebhook(
        val eventType: String,
        val contactId: Long,
        override val sourceSystem: String,
        override val revision: Int,
        @JsonProperty("X-Signature")
        val signatureHeader: String,
        val changedFields: List<String> = emptyList(),
    ) : SyncRecord()

    private val crmMapper = jacksonObjectMapper()

    private val partnerMapper = jsonMapper {
        addModule(kotlinModule())
        propertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
    }

    @Test
    fun readsLegacyExportWithAliasesFlatAddressAndUnknownColumns(): Unit {
        val export = """
            [
              {
                "contact_id": 501,
                "fullName": "Greta Lindqvist",
                "email_address": "Greta@Example.ORG",
                "billing_street_line": "Hauptstrasse 5",
                "billing_postal_code": "10115",
                "marketing_opt_in": true,
                "legacy_score": 87,
                "crm_internal": {"shard": 3}
              },
              {
                "contact_id": 502,
                "display_name_v1": "Omar Haddad",
                "email_address": "omar@partner.io",
                "billing_street_line": "Rue de Lyon 12",
                "billing_postal_code": "75012",
                "billing_country_code": "FR"
              },
              {
                "contact_id": 503,
                "display_name": "Mei Tanaka",
                "email_address": "mei@example.org",
                "billing_street_line": "Ringstrasse 1",
                "billing_postal_code": "1010",
                "billing_country_code": "AT"
              }
            ]
        """.trimIndent()

        val contacts: List<CrmContact> = crmMapper.readValue(export)

        assertThat(contacts).containsExactly(
            CrmContact(501, "Greta Lindqvist", "Greta@Example.ORG", PostalAddress("Hauptstrasse 5", "10115"), true),
            CrmContact(502, "Omar Haddad", "omar@partner.io", PostalAddress("Rue de Lyon 12", "75012", "FR")),
            CrmContact(503, "Mei Tanaka", "mei@example.org", PostalAddress("Ringstrasse 1", "1010", "AT")),
        )
        assertThat(contacts.groupBy { it.emailDomain }.mapValues { (_, group) -> group.map { it.contactId } })
            .containsExactlyInAnyOrderEntriesOf(mapOf("example.org" to listOf(501L, 503L), "partner.io" to listOf(502L)))
    }

    @Test
    fun writesSnakeCaseExportThatReadsBack(): Unit {
        val contact = CrmContact(
            contactId = 777,
            displayName = "Ana Souza",
            emailAddress = "ana@Shop.example",
            billingAddress = PostalAddress("Avenida Paulista 900", "01310-100", "BR"),
            marketingOptIn = true,
        )

        val json: String = crmMapper.writeValueAsString(contact)
        val tree: JsonNode = crmMapper.readTree(json)

        assertThat(tree.propertyNames()).containsExactlyInAnyOrder(
            "contact_id",
            "display_name",
            "email_address",
            "billing_street_line",
            "billing_postal_code",
            "billing_country_code",
            "marketing_opt_in",
            "email_domain",
        )
        assertThat(tree.path("billing_postal_code").asString()).isEqualTo("01310-100")
        assertThat(tree.path("email_domain").asString()).isEqualTo("shop.example")
        assertThat(tree.has("billing_address")).isFalse()

        val reread: CrmContact = crmMapper.readValue(json)
        assertThat(reread).isEqualTo(contact)
        assertThat(reread.emailDomain).isEqualTo("shop.example")
    }

    @Test
    fun mapsKebabCaseWebhooksWithInheritedProperties(): Unit {
        val payload = """
            {
              "event-type": "contact.updated",
              "contact-id": 501,
              "source-system": "partner-hub",
              "revision": 4,
              "X-Signature": "sha256=9f2c",
              "changed-fields": ["email_address", "marketing_opt_in"]
            }
        """.trimIndent()

        val webhook: PartnerWebhook = partnerMapper.readValue(payload)

        assertThat(webhook).isEqualTo(
            PartnerWebhook(
                eventType = "contact.updated",
                contactId = 501,
                sourceSystem = "partner-hub",
                revision = 4,
                signatureHeader = "sha256=9f2c",
                changedFields = listOf("email_address", "marketing_opt_in"),
            ),
        )
        val versioned: Versioned = webhook
        assertThat(versioned.revision).isEqualTo(4)

        val echoed: JsonNode = partnerMapper.valueToTree(webhook)
        assertThat(echoed.propertyNames()).containsExactlyInAnyOrder(
            "event-type",
            "contact-id",
            "source-system",
            "revision",
            "X-Signature",
            "changed-fields",
            "schema",
        )
        assertThat(echoed.path("schema").asString()).isEqualTo("crm-sync/partner-hub")

        val defaulted: PartnerWebhook = partnerMapper.readValue(
            """{"event-type": "contact.created", "contact-id": 9, "source-system": "partner-hub", "revision": 1, "X-Signature": "s"}""",
        )
        assertThat(defaulted.changedFields).isEmpty()
        assertThat(defaulted.schema).isEqualTo("crm-sync/partner-hub")
    }
}
