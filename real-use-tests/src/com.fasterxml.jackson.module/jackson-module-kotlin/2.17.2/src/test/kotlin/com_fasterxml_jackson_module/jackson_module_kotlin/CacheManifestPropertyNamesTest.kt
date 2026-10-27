/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_kotlin

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises Kotlin property-name discovery for case-sensitive cache manifest fields.
 * A content cache preserves the Kotlin eTag name when exporting validation tokens,
 * so a replacement worker can issue conditional requests using the same manifest schema.
 */
public class CacheManifestPropertyNamesTest {

    public data class CachedDocument(
        val resource: String,
        val eTag: String,
        val isFresh: Boolean,
    ) {
        public fun conditionalHeaders(): Map<String, String> =
            mapOf("If-None-Match" to eTag)
    }

    @Test
    @Timeout(60)
    public fun preservesManifestFieldNamesForConditionalContentRequests(): Unit {
        val module: KotlinModule = KotlinModule.Builder()
            .enable(KotlinFeature.KotlinPropertyNameAsImplicitName)
            .build()
        val mapper: ObjectMapper = ObjectMapper().registerModule(module)
        val manifestJson: String = """
            [
              {"resource": "/catalog.json", "eTag": "\"catalog-v7\"", "isFresh": false},
              {"resource": "/pricing.json", "eTag": "W/\"pricing-v3\"", "isFresh": true}
            ]
        """.trimIndent()

        val manifest: List<CachedDocument> = mapper.readValue(manifestJson)
        assertEquals(
            listOf(
                CachedDocument("/catalog.json", "\"catalog-v7\"", false),
                CachedDocument("/pricing.json", "W/\"pricing-v3\"", true),
            ),
            manifest,
        )
        val revalidationRequests: Map<String, Map<String, String>> = manifest
            .filterNot { document: CachedDocument -> document.isFresh }
            .associate { document: CachedDocument -> document.resource to document.conditionalHeaders() }
        assertEquals(
            mapOf("/catalog.json" to mapOf("If-None-Match" to "\"catalog-v7\"")),
            revalidationRequests,
        )

        val refreshedManifest: List<CachedDocument> = manifest.map { document: CachedDocument ->
            document.copy(isFresh = true)
        }
        val exportedJson: String = mapper.writeValueAsString(refreshedManifest)
        val exported: JsonNode = mapper.readTree(exportedJson)
        val expectedExport: JsonNode = mapper.readTree(
            """
                [
                  {"resource": "/catalog.json", "eTag": "\"catalog-v7\"", "isFresh": true},
                  {"resource": "/pricing.json", "eTag": "W/\"pricing-v3\"", "isFresh": true}
                ]
            """.trimIndent(),
        )
        assertEquals(expectedExport, exported)
        assertFalse(exported[0].has("etag"))
        assertFalse(exported[0].has("fresh"))

        val restoredManifest: List<CachedDocument> = mapper.readValue(exportedJson)
        assertEquals(refreshedManifest, restoredManifest)
        assertEquals(mapOf("If-None-Match" to "\"catalog-v7\""), restoredManifest[0].conditionalHeaders())
        assertEquals(mapOf("If-None-Match" to "W/\"pricing-v3\""), restoredManifest[1].conditionalHeaders())
    }
}
