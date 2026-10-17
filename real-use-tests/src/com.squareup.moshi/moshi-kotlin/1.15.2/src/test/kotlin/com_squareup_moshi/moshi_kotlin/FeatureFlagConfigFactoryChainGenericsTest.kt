/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_moshi.moshi_kotlin

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.JsonQualifier
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * Exercises `JsonAdapter.Factory` chains around generic Kotlin classes: application factories
 * that wrap `KotlinJsonAdapterFactory` adapters through `Moshi.nextAdapter` and a `@JsonQualifier`,
 * type-variable resolution in generic data classes, `Types` type construction, and builder layering.
 *
 * A feature-flag service fetches its remote configuration as a versioned envelope, validates every
 * setting after decoding, and lets operators apply hand-edited, comment-bearing override files.
 */
@OptIn(ExperimentalStdlibApi::class)
public class FeatureFlagConfigFactoryChainGenericsTest {

    /** Decoded models that can reject themselves after binding. */
    public interface Validated {
        /** Problems found in this value; empty when it is usable. */
        public fun validationErrors(): List<String>
    }

    /** Marks a property that travels inside a `{"version":..,"payload":..}` envelope. */
    @Retention(AnnotationRetention.RUNTIME)
    @JsonQualifier
    public annotation class Enveloped(val version: Int)

    /** One named flag value together with the values it replaced. */
    public data class Setting<T>(
        val key: String,
        val value: T,
        val previous: List<T> = emptyList(),
    ) : Validated {
        override fun validationErrors(): List<String> =
            if (KEY_PATTERN.matches(key)) emptyList() else listOf("invalid key '$key'")
    }

    /** Cohort assignments keyed by a cohort identifier. */
    public data class Rollout<K, V>(val cohorts: Map<K, List<V>>) : Validated {
        override fun validationErrors(): List<String> =
            cohorts.filterValues { it.isEmpty() }.keys.map { "empty cohort '$it'" }
    }

    /** Every flag the service knows, grouped by value type. */
    public data class FlagSet(
        val booleans: List<Setting<Boolean>>,
        val limits: Map<String, Setting<Int>>,
        val rollout: Rollout<String, Long>,
    )

    /** The document the configuration endpoint returns. */
    public data class RemoteConfig(
        val service: String,
        @Enveloped(version = 3) val flags: FlagSet,
        val fetchedBy: String = "config-agent",
    )

    /** Runs `validationErrors` on every unqualified `Validated` value the next factory decodes. */
    public class ValidatingFactory : JsonAdapter.Factory {
        override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
            if (annotations.isNotEmpty()) return null
            if (!Validated::class.java.isAssignableFrom(Types.getRawType(type))) return null
            val delegate: JsonAdapter<Any> = moshi.nextAdapter(this, type, annotations)
            return ValidatingAdapter(delegate)
        }
    }

    /** Delegates encoding unchanged and checks each decoded value. */
    public class ValidatingAdapter(private val delegate: JsonAdapter<Any>) : JsonAdapter<Any>() {
        override fun fromJson(reader: JsonReader): Any? {
            val path: String = reader.path
            val value: Any? = delegate.fromJson(reader)
            val errors: List<String> = (value as Validated?)?.validationErrors().orEmpty()
            if (errors.isNotEmpty()) throw JsonDataException("${errors.joinToString()} at $path")
            return value
        }

        override fun toJson(writer: JsonWriter, value: Any?) {
            delegate.toJson(writer, value)
        }
    }

    /** Resolves `@Enveloped` and hands the remaining qualifiers back to the chain. */
    public class EnvelopeFactory : JsonAdapter.Factory {
        override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
            val remaining: Set<Annotation> =
                Types.nextAnnotations(annotations, Enveloped::class.java) ?: return null
            val version: Int = annotations.filterIsInstance<Enveloped>().single().version
            return EnvelopeAdapter(version, moshi.adapter(type, remaining))
        }
    }

    /** Wraps the payload with its schema version and refuses any other version. */
    public class EnvelopeAdapter(
        private val version: Int,
        private val payload: JsonAdapter<Any>,
    ) : JsonAdapter<Any>() {
        override fun fromJson(reader: JsonReader): Any? {
            val path: String = reader.path
            var seenVersion: Int? = null
            var value: Any? = null
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "version" -> seenVersion = reader.nextInt()
                    "payload" -> value = payload.fromJson(reader)
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            if (seenVersion != version) {
                throw JsonDataException("expected envelope version $version but was $seenVersion at $path")
            }
            return value ?: throw JsonDataException("missing payload at $path")
        }

        override fun toJson(writer: JsonWriter, value: Any?) {
            writer.beginObject()
            writer.name("version").value(version.toLong())
            writer.name("payload")
            payload.toJson(writer, value)
            writer.endObject()
        }
    }

    private companion object {
        val KEY_PATTERN: Regex = Regex("[a-z][a-z0-9_.]*")

        val REMOTE_DOCUMENT: String = """
            {
              "service": "checkout",
              "flags": {
                "payload": {
                  "booleans": [
                    {"key": "express_pay", "value": true, "previous": [false]},
                    {"key": "legacy_cart", "value": false}
                  ],
                  "limits": {
                    "max_upload_mb": {"key": "max_upload_mb", "value": 250, "previous": [100, 200]}
                  },
                  "rollout": {"cohorts": {"beta": [101, 102], "staff": [7]}}
                },
                "version": 3,
                "signature": "ignored-by-clients"
              }
            }
        """.trimIndent()
    }

    /** Base instance every service shares; it binds Kotlin classes but validates nothing. */
    private val baseMoshi: Moshi = Moshi.Builder()
        .add(EnvelopeFactory())
        .addLast(KotlinJsonAdapterFactory())
        .build()

    /** The configuration client's instance, layered on the base one. */
    private val moshi: Moshi = baseMoshi.newBuilder()
        .add(ValidatingFactory())
        .build()

    private val configAdapter: JsonAdapter<RemoteConfig> = moshi.adapter<RemoteConfig>()

    @Test
    fun decodesEnvelopedRemoteConfigWithResolvedTypeArguments() {
        val config: RemoteConfig = configAdapter.fromJson(REMOTE_DOCUMENT)!!

        assertEquals("checkout", config.service)
        assertEquals("config-agent", config.fetchedBy)
        assertEquals(
            listOf(Setting("express_pay", true, listOf(false)), Setting("legacy_cart", false)),
            config.flags.booleans,
        )
        val upload: Setting<Int> = config.flags.limits.getValue("max_upload_mb")
        assertEquals(250, upload.value)
        assertEquals(listOf(100, 200), upload.previous)
        assertEquals(listOf(101L, 102L), config.flags.rollout.cohorts.getValue("beta"))
        assertEquals(listOf(7L), config.flags.rollout.cohorts.getValue("staff"))
    }

    @Test
    fun encodesFlagsInsideVersionedEnvelopeAndReadsThemBack() {
        val config = RemoteConfig(
            service = "search",
            flags = FlagSet(
                booleans = listOf(Setting("typo_tolerance", true)),
                limits = mapOf("page_size" to Setting("page_size", 50, listOf(20))),
                rollout = Rollout(mapOf("eu" to listOf(9L))),
            ),
            fetchedBy = "warmup-job",
        )

        val json: String = configAdapter.toJson(config)

        assertEquals(
            """{"service":"search","flags":{"version":3,"payload":{"booleans":[""" +
                """{"key":"typo_tolerance","value":true,"previous":[]}],"limits":{"page_size":""" +
                """{"key":"page_size","value":50,"previous":[20]}},"rollout":{"cohorts":""" +
                """{"eu":[9]}}}},"fetchedBy":"warmup-job"}""",
            json,
        )
        assertEquals(config, configAdapter.fromJson(json))
    }

    @Test
    fun rejectsEnvelopeFromAnotherSchemaVersion() {
        val stale: String = REMOTE_DOCUMENT.replace("\"version\": 3", "\"version\": 2")

        val failure: JsonDataException = assertThrows(JsonDataException::class.java) {
            configAdapter.fromJson(stale)
        }

        assertEquals("expected envelope version 3 but was 2 at $.flags", failure.message)
    }

    @Test
    fun validatingFactoryRejectsSettingsAtTheirJsonPath() {
        val badKey: String = REMOTE_DOCUMENT.replace("\"legacy_cart\"", "\"Legacy Cart\"")
        val emptyCohort: String = REMOTE_DOCUMENT.replace("\"staff\": [7]", "\"staff\": []")

        val keyFailure: JsonDataException = assertThrows(JsonDataException::class.java) {
            configAdapter.fromJson(badKey)
        }
        val cohortFailure: JsonDataException = assertThrows(JsonDataException::class.java) {
            configAdapter.fromJson(emptyCohort)
        }

        assertEquals("invalid key 'Legacy Cart' at $.flags.payload.booleans[1]", keyFailure.message)
        assertEquals("empty cohort 'staff' at $.flags.payload.rollout", cohortFailure.message)
    }

    @Test
    fun baseInstanceWithoutTheValidatingFactoryAcceptsTheSameDocument() {
        val badKey: String = REMOTE_DOCUMENT.replace("\"legacy_cart\"", "\"Legacy Cart\"")

        val config: RemoteConfig = baseMoshi.adapter<RemoteConfig>().fromJson(badKey)!!

        assertEquals("Legacy Cart", config.flags.booleans[1].key)
        assertEquals(listOf("invalid key 'Legacy Cart'"), config.flags.booleans[1].validationErrors())
    }

    @Test
    fun explicitlyBuiltParameterizedTypeMatchesTheReifiedAdapter() {
        val regionsType: ParameterizedType = Types.newParameterizedTypeWithOwner(
            FeatureFlagConfigFactoryChainGenericsTest::class.java,
            Setting::class.java,
            Types.newParameterizedType(List::class.java, String::class.java),
        )
        val explicit: JsonAdapter<Setting<List<String>>> = moshi.adapter(regionsType)
        val reified: JsonAdapter<Setting<List<String>>> = moshi.adapter<Setting<List<String>>>()
        val json = """{"key":"allowed_regions","value":["eu","us"],"previous":[["eu"]]}"""

        val decoded: Setting<List<String>> = explicit.fromJson(json)!!

        assertEquals(Setting("allowed_regions", listOf("eu", "us"), listOf(listOf("eu"))), decoded)
        assertEquals(decoded, reified.fromJson(json))
        assertEquals(json, reified.toJson(decoded))
        assertEquals(Setting::class.java, Types.getRawType(regionsType))
        assertThrows(JsonDataException::class.java) {
            explicit.fromJson("""{"key":"BAD","value":[]}""")
        }
    }

    @Test
    fun appliesHandEditedOverrideFileOnlyInLenientMode() {
        val overrides = """
            // operator overrides, applied after the remote document
            [
              {key: 'express_pay', value: false},  # incident rollback
              {key: 'dark_mode', value: true, previous: [false]}
            ]
        """.trimIndent()
        val overrideAdapter: JsonAdapter<List<Setting<Boolean>>> = moshi.adapter<List<Setting<Boolean>>>()
        val remote: RemoteConfig = configAdapter.fromJson(REMOTE_DOCUMENT)!!

        assertThrows(JsonEncodingException::class.java) { overrideAdapter.fromJson(overrides) }
        val applied: List<Setting<Boolean>> = overrideAdapter.lenient().fromJson(overrides)!!
        val merged: Map<String, Boolean> =
            (remote.flags.booleans + applied).associate { it.key to it.value }

        assertEquals(mapOf("express_pay" to false, "legacy_cart" to false, "dark_mode" to true), merged)
        assertTrue(applied.all { it.validationErrors().isEmpty() })
    }
}
