/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_kotlin

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.contains
import com.fasterxml.jackson.module.kotlin.minusAssign
import com.fasterxml.jackson.module.kotlin.plusAssign
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises Kotlin JSON-tree mutation operators for sanitized deployment configuration exports.
 * A deployment service removes private settings and replaces a local audit destination before
 * publishing a configuration, preserving extension fields it does not model as application types.
 */
public class DeploymentTreeSanitizationTest {

    @Test
    @Timeout(60)
    public fun publishesRedactedConfigurationWithoutChangingPrivateSettings(): Unit {
        val module: KotlinModule = KotlinModule.Builder().build()
        val mapper: ObjectMapper = ObjectMapper().registerModule(module)
        val privateJson: String = """
            {
              "service": "billing",
              "clientSecret": "deployment-only-token",
              "internalNotes": "Rotate credentials before the next release",
              "debug": true,
              "destinations": ["local-console", "audit-store"],
              "retentionDays": 30,
              "vendorOptions": {"format": "ndjson", "compression": "gzip"}
            }
        """.trimIndent()
        val privateConfiguration: ObjectNode = mapper.readTree(privateJson) as ObjectNode
        val publicConfiguration: ObjectNode = privateConfiguration.deepCopy()

        publicConfiguration -= listOf("clientSecret", "internalNotes")
        if ("debug" in publicConfiguration) {
            publicConfiguration -= "debug"
        }
        val destinations: ArrayNode = publicConfiguration["destinations"] as ArrayNode
        val localDestinationIndex: Int = destinations.indexOfFirst { destination: JsonNode ->
            destination.asText() == "local-console"
        }
        if (localDestinationIndex >= 0) {
            destinations -= localDestinationIndex
        }
        destinations += "compliance-store"

        val exportedJson: String = mapper.writeValueAsString(publicConfiguration)
        val deployedConfiguration: JsonNode = mapper.readTree(exportedJson)
        val expectedConfiguration: JsonNode = mapper.readTree(
            """
                {
                  "service": "billing",
                  "destinations": ["audit-store", "compliance-store"],
                  "retentionDays": 30,
                  "vendorOptions": {"format": "ndjson", "compression": "gzip"}
                }
            """.trimIndent(),
        )
        assertEquals(expectedConfiguration, deployedConfiguration)
        assertFalse("clientSecret" in deployedConfiguration)
        assertFalse("internalNotes" in deployedConfiguration)
        assertFalse("debug" in deployedConfiguration)
        assertTrue("vendorOptions" in deployedConfiguration)
        assertEquals(mapper.readTree(privateJson), privateConfiguration)
        assertEquals("deployment-only-token", privateConfiguration["clientSecret"].asText())
    }
}
