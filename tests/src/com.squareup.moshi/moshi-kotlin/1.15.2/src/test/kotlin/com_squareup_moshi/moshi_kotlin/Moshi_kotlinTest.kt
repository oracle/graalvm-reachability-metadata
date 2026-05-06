/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_moshi.moshi_kotlin

import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonQualifier
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.lang.reflect.Type
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

public class MoshiKotlinTest {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun readsKotlinPrimaryConstructorWithJsonNamesNullabilityAndDefaults(): Unit {
        val adapter = moshi.adapter(UserProfile::class.java)

        val profile = adapter.fromJson(
            """
            {
              "user_id": 7,
              "name": "Ada Lovelace",
              "unknown_from_service": true
            }
            """.trimIndent(),
        )

        assertThat(profile).isEqualTo(
            UserProfile(
                userId = 7,
                name = "Ada Lovelace",
                nicknames = emptyList(),
                settings = AccountSettings(),
                email = null,
                enabled = true,
            ),
        )
    }

    @Test
    fun writesNestedKotlinObjectsUsingDeclarationOrderAndJsonNames(): Unit {
        val adapter = moshi.adapter(UserProfile::class.java)
        val profile = UserProfile(
            userId = 8,
            name = "Grace Hopper",
            nicknames = listOf("Amazing Grace", "grandma COBOL"),
            settings = AccountSettings(newsletters = true, theme = "navy"),
            email = "grace@example.test",
            enabled = false,
        )

        assertThat(adapter.toJson(profile)).isEqualTo(
            """
            {"user_id":8,"name":"Grace Hopper","nicknames":["Amazing Grace","grandma COBOL"],"settings":{"newsletters":true,"theme":"navy"},"email":"grace@example.test","enabled":false}
            """.trimIndent(),
        )
    }

    @Test
    fun serializesExplicitNullsForNullableKotlinPropertiesWhenRequested(): Unit {
        val adapter = moshi.adapter(UserProfile::class.java).serializeNulls()
        val profile = UserProfile(userId = 9, name = "Katherine Johnson")

        assertThat(adapter.toJson(profile)).isEqualTo(
            """
            {"user_id":9,"name":"Katherine Johnson","nicknames":[],"settings":{"newsletters":false,"theme":"light"},"email":null,"enabled":true}
            """.trimIndent(),
        )
    }

    @Test
    fun rejectsNullAndMissingValuesForNonNullableKotlinProperties(): Unit {
        val adapter = moshi.adapter(RequiredBooking::class.java)

        assertThatThrownBy { adapter.fromJson("""{"guest":null,"nights":2}""") }
            .isInstanceOf(JsonDataException::class.java)
            .hasMessageContaining("guest")
            .hasMessageContaining("null")

        assertThatThrownBy { adapter.fromJson("""{"nights":2}""") }
            .isInstanceOf(JsonDataException::class.java)
            .hasMessageContaining("guest")
            .hasMessageContaining("missing")
    }

    @Test
    fun ignoresPropertiesMarkedWithJsonIgnoreAndAppliesTheirDefaults(): Unit {
        val adapter = moshi.adapter(AuditEntry::class.java)

        val entry = adapter.fromJson(
            """
            {
              "event_id": "evt-1",
              "actor": "system",
              "localOnlyNote": "must not be read"
            }
            """.trimIndent(),
        )

        assertThat(entry).isEqualTo(
            AuditEntry(
                eventId = "evt-1",
                actor = "system",
                localOnlyNote = "created locally",
            ),
        )
        assertThat(adapter.toJson(entry)).isEqualTo("""{"event_id":"evt-1","actor":"system"}""")
    }

    @Test
    fun readsAndWritesKotlinClassesContainingCollectionsMapsEnumsAndNestedObjects(): Unit {
        val adapter = moshi.adapter(IncidentReport::class.java)
        val json =
            """
            {
              "severity": "HIGH",
              "entries": [
                {"event_id":"evt-2","actor":"scheduler"},
                {"event_id":"evt-3","actor":"operator"}
              ],
              "countsByActor": {
                "scheduler": 1,
                "operator": 1
              }
            }
            """.trimIndent()

        val report = adapter.fromJson(json)

        assertThat(report).isEqualTo(
            IncidentReport(
                severity = Severity.HIGH,
                entries = listOf(
                    AuditEntry(eventId = "evt-2", actor = "scheduler"),
                    AuditEntry(eventId = "evt-3", actor = "operator"),
                ),
                countsByActor = mapOf("scheduler" to 1, "operator" to 1),
            ),
        )
        assertThat(adapter.toJson(report)).isEqualTo(
            """
            {"severity":"HIGH","entries":[{"event_id":"evt-2","actor":"scheduler"},{"event_id":"evt-3","actor":"operator"}],"countsByActor":{"scheduler":1,"operator":1}}
            """.trimIndent(),
        )
    }

    @Test
    fun appliesJsonQualifierAnnotationsOnKotlinConstructorProperties(): Unit {
        val qualifiedMoshi = Moshi.Builder()
            .add(NormalizedChannelStringAdapterFactory)
            .addLast(KotlinJsonAdapterFactory())
            .build()
        val adapter = qualifiedMoshi.adapter(SupportTicket::class.java)

        val ticket = adapter.fromJson(
            """
            {
              "channel": "email",
              "summary": "Needs HELP"
            }
            """.trimIndent(),
        )

        assertThat(ticket).isEqualTo(
            SupportTicket(
                channel = "EMAIL",
                summary = "Needs HELP",
            ),
        )
        assertThat(adapter.toJson(ticket)).isEqualTo("""{"channel":"email","summary":"Needs HELP"}""")
    }
}

public data class UserProfile(
    @Json(name = "user_id")
    val userId: Long,
    val name: String,
    val nicknames: List<String> = emptyList(),
    val settings: AccountSettings = AccountSettings(),
    val email: String? = null,
    val enabled: Boolean = true,
)

public data class AccountSettings(
    val newsletters: Boolean = false,
    val theme: String = "light",
)

public data class RequiredBooking(
    val guest: String,
    val nights: Int,
)

public data class AuditEntry(
    @Json(name = "event_id")
    val eventId: String,
    val actor: String,
    @Json(ignore = true)
    val localOnlyNote: String = "created locally",
)

public enum class Severity {
    LOW,
    HIGH,
}

public data class IncidentReport(
    val severity: Severity,
    val entries: List<AuditEntry>,
    val countsByActor: Map<String, Int>,
)

@JsonQualifier
@Retention(AnnotationRetention.RUNTIME)
public annotation class NormalizedChannel

public data class SupportTicket(
    @NormalizedChannel
    val channel: String,
    val summary: String,
)

public object NormalizedChannelStringAdapterFactory : JsonAdapter.Factory {
    override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
        if (type != String::class.java || annotations.none { it is NormalizedChannel }) {
            return null
        }
        return NormalizedChannelStringAdapter
    }
}

public object NormalizedChannelStringAdapter : JsonAdapter<String>() {
    override fun fromJson(reader: JsonReader): String {
        return reader.nextString().uppercase()
    }

    override fun toJson(writer: JsonWriter, value: String?) {
        writer.value(requireNotNull(value) { "channel == null" }.lowercase())
    }
}
