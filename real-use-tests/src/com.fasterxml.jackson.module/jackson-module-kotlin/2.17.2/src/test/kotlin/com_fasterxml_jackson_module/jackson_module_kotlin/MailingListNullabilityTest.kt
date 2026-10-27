/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_kotlin

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises Kotlin strict collection nullability for mailing-list imports.
 * A campaign intake rejects null recipients before preparing delivery jobs, while allowing
 * explicitly nullable personalization values to select the standard greeting.
 */
public class MailingListNullabilityTest {

    public data class CampaignImport(
        val campaignId: String,
        val recipients: List<String>,
        val personalization: Map<String, String?>,
    )

    @Test
    @Timeout(60)
    public fun validatesRecipientListsAndPreparesPersonalizedDeliveryJobs(): Unit {
        val module: KotlinModule = KotlinModule.Builder()
            .enable(KotlinFeature.StrictNullChecks)
            .build()
        val mapper: ObjectMapper = ObjectMapper().registerModule(module)
        val malformedImport: String = """
            {
              "campaignId": "autumn-news",
              "recipients": ["alice@example.com", null],
              "personalization": {"alice@example.com": "Alice"}
            }
        """.trimIndent()

        val rejection: JsonMappingException = assertThrows(JsonMappingException::class.java) {
            mapper.readValue<CampaignImport>(malformedImport)
        }
        assertTrue(rejection.path.any { reference: JsonMappingException.Reference ->
            reference.fieldName == "recipients"
        })

        val correctedImport: String = """
            {
              "campaignId": "autumn-news",
              "recipients": ["alice@example.com", "bob@example.com"],
              "personalization": {"alice@example.com": "Alice", "bob@example.com": null}
            }
        """.trimIndent()
        val campaign: CampaignImport = mapper.readValue(correctedImport)

        assertEquals("autumn-news", campaign.campaignId)
        assertEquals(listOf("alice@example.com", "bob@example.com"), campaign.recipients)
        assertEquals(
            mapOf("alice@example.com" to "Alice", "bob@example.com" to null),
            campaign.personalization,
        )
        val deliveryJobs: List<String> = campaign.recipients.map { recipient: String ->
            val greeting: String = campaign.personalization[recipient] ?: "subscriber"
            "${campaign.campaignId}|$recipient|Hello, $greeting!"
        }
        assertEquals(
            listOf(
                "autumn-news|alice@example.com|Hello, Alice!",
                "autumn-news|bob@example.com|Hello, subscriber!",
            ),
            deliveryJobs,
        )

        val archivedJson: String = mapper.writeValueAsString(campaign)
        val archived: JsonNode = mapper.readTree(archivedJson)
        assertEquals(mapper.readTree(correctedImport), archived)
        assertTrue(archived["personalization"]["bob@example.com"].isNull)
        val restored: CampaignImport = mapper.readValue(archivedJson)
        assertEquals(campaign, restored)
    }
}
