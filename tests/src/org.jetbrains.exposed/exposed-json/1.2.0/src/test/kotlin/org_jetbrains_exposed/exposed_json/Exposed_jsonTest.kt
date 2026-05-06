/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_exposed.exposed_json

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.CastToJson
import org.jetbrains.exposed.v1.core.JsonColumnMarker
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.stringLiteral
import org.jetbrains.exposed.v1.json.JsonBColumnType
import org.jetbrains.exposed.v1.json.JsonColumnType
import org.jetbrains.exposed.v1.json.castToJson
import org.jetbrains.exposed.v1.json.contains
import org.jetbrains.exposed.v1.json.exists
import org.jetbrains.exposed.v1.json.extract
import org.jetbrains.exposed.v1.json.json
import org.jetbrains.exposed.v1.json.jsonb
import org.junit.jupiter.api.Test

public class Exposed_jsonTest {
    @Test
    fun jsonColumnTypeConvertsDatabaseValuesWithCustomCodecs() {
        val columnType = JsonColumnType(::encodeProfile, ::decodeProfile)
        val profile = Profile("Ada", active = true, tags = listOf("admin", "json"))
        val serialized = """{"name":"Ada","active":true,"tags":["admin","json"]}"""

        assertThat(columnType.usesBinaryFormat).isFalse()
        assertThat(columnType.needsBinaryFormatCast).isFalse()
        assertThat(columnType.notNullValueToDB(profile)).isEqualTo(serialized)
        assertThat(columnType.valueFromDB(serialized)).isEqualTo(profile)
        assertThat(columnType.valueFromDB(serialized.encodeToByteArray())).isEqualTo(profile)
        assertThat(columnType.valueFromDB(profile)).isSameAs(profile)
    }

    @Test
    fun jsonbColumnTypeKeepsJsonCodecsAndExposesBinaryFormatFlags() {
        val plainJsonb = JsonBColumnType(::encodeProfile, ::decodeProfile, castToJsonFormat = false)
        val castingJsonb = JsonBColumnType(::encodeProfile, ::decodeProfile, castToJsonFormat = true)
        val profile = Profile("Grace", active = false, tags = listOf("compiler", "database"))
        val serialized = """{"name":"Grace","active":false,"tags":["compiler","database"]}"""

        assertThat(plainJsonb.usesBinaryFormat).isTrue()
        assertThat(plainJsonb.needsBinaryFormatCast).isFalse()
        assertThat(castingJsonb.usesBinaryFormat).isTrue()
        assertThat(castingJsonb.needsBinaryFormatCast).isTrue()
        assertThat(castingJsonb.notNullValueToDB(profile)).isEqualTo(serialized)
        assertThat(castingJsonb.valueFromDB(serialized)).isEqualTo(profile)
        assertThat(castingJsonb.valueFromDB(serialized.encodeToByteArray())).isEqualTo(profile)
    }

    @Test
    fun tableExtensionsRegisterJsonAndJsonbColumnsUsingProvidedSerializers() {
        val table = object : Table("serializer_documents") {
            val jsonTags = json<List<String>>("json_tags", Json, ListSerializer(String.serializer()))
            val jsonbTags = jsonb<List<String>>(
                "jsonb_tags",
                Json,
                ListSerializer(String.serializer()),
                castToJsonFormat = true
            )
        }
        val tags = listOf("native", "metadata", "exposed")

        assertThat(table.columns).containsExactly(table.jsonTags, table.jsonbTags)
        assertThat(table.jsonTags.columnType).isInstanceOf(JsonColumnMarker::class.java)
        assertThat(table.jsonTags.columnType).isInstanceOf(JsonColumnType::class.java)
        assertThat(table.jsonbTags.columnType).isInstanceOf(JsonBColumnType::class.java)
        assertThat(table.jsonTags.columnType.notNullValueToDB(tags)).isEqualTo("""["native","metadata","exposed"]""")
        assertThat(table.jsonTags.columnType.valueFromDB("""["native","metadata","exposed"]""")).isEqualTo(tags)
        assertThat(table.jsonbTags.columnType.notNullValueToDB(tags)).isEqualTo("""["native","metadata","exposed"]""")
        assertThat(table.jsonbTags.columnType.valueFromDB("""["native","metadata","exposed"]""".encodeToByteArray()))
            .isEqualTo(tags)
    }

    @Test
    fun jsonConditionExtensionsCreateExpressionsWithTargetCandidateAndPathMetadata() {
        val table = object : Table("condition_documents") {
            val payload = json("payload", ::encodeProfile, ::decodeProfile)
        }
        val containsValue = table.payload.contains("""{"active":true}""", path = ".active")
        val containsExpression = table.payload.contains(stringLiteral("""{"tags":["json"]}"""))
        val existsAtPath = table.payload.exists(".tags[0]", ".name", optional = "one")

        assertThat(containsValue.target).isSameAs(table.payload)
        assertThat(containsValue.path).isEqualTo(".active")
        assertThat(containsValue.jsonType).isSameAs(table.payload.columnType)
        assertThat(containsExpression.target).isSameAs(table.payload)
        assertThat(containsExpression.path).isNull()
        assertThat(containsExpression.candidate).isNotNull()
        assertThat(existsAtPath.expression).isSameAs(table.payload)
        assertThat(existsAtPath.path).containsExactly(".tags[0]", ".name")
        assertThat(existsAtPath.optional).isEqualTo("one")
        assertThat(existsAtPath.jsonType).isSameAs(table.payload.columnType)
    }

    @Test
    fun jsonFunctionExtensionsCreateExtractAndCastExpressions() {
        val table = object : Table("function_documents") {
            val payload = json("payload", ::encodeProfile, ::decodeProfile)
            val rawPayload = text("raw_payload")
        }
        val extractedName = table.payload.extract<String>(".name")
        val extractedTags = table.payload.extract<List<String>>(".tags", toScalar = false)
        val castRawPayload = table.rawPayload.castToJson<Profile>()

        assertThat(extractedName.expression).isSameAs(table.payload)
        assertThat(extractedName.path).containsExactly(".name")
        assertThat(extractedName.toScalar).isTrue()
        assertThat(extractedName.jsonType).isSameAs(table.payload.columnType)
        assertThat(extractedTags.expression).isSameAs(table.payload)
        assertThat(extractedTags.path).containsExactly(".tags")
        assertThat(extractedTags.toScalar).isFalse()
        assertThat(extractedTags.jsonType).isSameAs(table.payload.columnType)
        assertThat(castRawPayload).isInstanceOf(CastToJson::class.java)
        assertThat(castRawPayload.expression).isSameAs(table.rawPayload)
        assertThat(castRawPayload.columnType).isInstanceOf(JsonColumnType::class.java)
    }

    private data class Profile(val name: String, val active: Boolean, val tags: List<String>)

    private companion object {
        private val json = Json { ignoreUnknownKeys = true }

        private fun encodeProfile(profile: Profile): String {
            return buildJsonObject {
                put("name", JsonPrimitive(profile.name))
                put("active", JsonPrimitive(profile.active))
                put("tags", JsonArray(profile.tags.map(::JsonPrimitive)))
            }.toString()
        }

        private fun decodeProfile(value: String): Profile {
            val parsed: JsonObject = json.parseToJsonElement(value).jsonObject
            return Profile(
                name = parsed.getValue("name").jsonPrimitive.content,
                active = parsed.getValue("active").jsonPrimitive.boolean,
                tags = parsed.getValue("tags").jsonArray.map { it.jsonPrimitive.content }
            )
        }
    }
}
