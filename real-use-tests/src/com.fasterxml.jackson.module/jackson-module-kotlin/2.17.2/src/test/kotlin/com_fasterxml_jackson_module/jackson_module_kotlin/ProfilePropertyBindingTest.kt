/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_kotlin

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.properties.Delegates

/**
 * Exercises Kotlin post-constructor binding of lateinit and delegated properties for profile provisioning.
 * A provisioning service reads account details into a model with required mutable properties,
 * prepares an invitation, and exports the updated license allocation for another worker.
 */
public class ProfilePropertyBindingTest {

    public class ProvisionedProfile(val accountId: String) {
        public lateinit var displayName: String
        public lateinit var email: String
        public var licensedSeats: Int by Delegates.notNull()

        public fun invitation(): String =
            "$email|Welcome, $displayName! Account $accountId has $licensedSeats seats."
    }

    @Test
    @Timeout(60)
    public fun provisionsMutableProfileAndExportsUpdatedLicenseAllocation(): Unit {
        val module: KotlinModule = KotlinModule.Builder().build()
        val mapper: ObjectMapper = ObjectMapper().registerModule(module)
        val provisioningJson: String = """
            {
              "accountId": "account-204",
              "displayName": "Avery Quinn",
              "email": "avery@example.com",
              "licensedSeats": 3
            }
        """.trimIndent()

        val profile: ProvisionedProfile = mapper.readValue(provisioningJson)

        assertEquals("account-204", profile.accountId)
        assertEquals("Avery Quinn", profile.displayName)
        assertEquals("avery@example.com", profile.email)
        assertEquals(3, profile.licensedSeats)
        assertEquals(
            "avery@example.com|Welcome, Avery Quinn! Account account-204 has 3 seats.",
            profile.invitation(),
        )

        profile.displayName = "Avery's Workshop"
        profile.licensedSeats += 2
        val exportedJson: String = mapper.writeValueAsString(profile)
        val exported: JsonNode = mapper.readTree(exportedJson)
        val expectedExport: JsonNode = mapper.readTree(
            """
                {
                  "accountId": "account-204",
                  "displayName": "Avery's Workshop",
                  "email": "avery@example.com",
                  "licensedSeats": 5
                }
            """.trimIndent(),
        )
        assertEquals(expectedExport, exported)

        val restored: ProvisionedProfile = mapper.readValue(exportedJson)
        assertEquals("account-204", restored.accountId)
        assertEquals("Avery's Workshop", restored.displayName)
        assertEquals("avery@example.com", restored.email)
        assertEquals(5, restored.licensedSeats)
        assertEquals(
            "avery@example.com|Welcome, Avery's Workshop! Account account-204 has 5 seats.",
            restored.invitation(),
        )
    }
}
