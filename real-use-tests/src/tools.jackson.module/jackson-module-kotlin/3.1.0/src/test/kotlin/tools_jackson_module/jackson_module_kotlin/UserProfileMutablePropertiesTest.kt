/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools_jackson_module.jackson_module_kotlin

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.JsonView
import com.fasterxml.jackson.annotation.Nulls
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jsonMapper
import tools.jackson.module.kotlin.kotlinModule
import tools.jackson.module.kotlin.readValue

/**
 * Mixed constructor and property binding of non-data Kotlin classes: creator parameters with
 * defaults combined with body `var` and `lateinit var` properties, null-skipping setters,
 * computed getter-only properties, `@JsonAnySetter`/`@JsonAnyGetter` preference bags, `@JsonView`
 * filtering, and in-place PATCH updates through `readerForUpdating`, as a user-profile service
 * accepts sign-ups, publishes public and internal views, and applies partial edits.
 */
public class UserProfileMutablePropertiesTest {

    class Views {
        interface Public

        interface Internal : Public
    }

    enum class Theme { LIGHT, DARK, SYSTEM }

    @JsonPropertyOrder("userId", "createdBy", "displayName", "initials", "theme", "bio", "tags")
    class UserProfile(val userId: String, val createdBy: String = "self-service") {
        lateinit var displayName: String

        var bio: String? = null

        @JsonSetter(nulls = Nulls.SKIP)
        var theme: Theme = Theme.SYSTEM

        var tags: MutableList<String> = mutableListOf()

        @JsonView(Views.Internal::class)
        var lastLoginIp: String? = null

        val initials: String
            get() = displayName.split(' ')
                .filter { it.isNotEmpty() }
                .joinToString("") { it.first().uppercase() }

        private val preferences: MutableMap<String, Any?> = linkedMapOf()

        @JsonAnySetter
        fun putPreference(key: String, value: Any?) {
            preferences[key] = value
        }

        @JsonAnyGetter
        fun preferences(): Map<String, Any?> = preferences
    }

    private val mapper: ObjectMapper = jsonMapper {
        addModule(kotlinModule())
        enable(MapperFeature.DEFAULT_VIEW_INCLUSION)
    }

    private val signUp: String = """
        {
          "userId": "u-17",
          "displayName": "Ada King Lovelace",
          "theme": null,
          "tags": ["beta", "early-adopter"],
          "newsletter": true,
          "locale": "en-GB"
        }
    """.trimIndent()

    @Test
    fun bindsSignUpThroughConstructorThenSettersAndCollectsUnknownFieldsAsPreferences() {
        val profile: UserProfile = mapper.readValue(signUp)

        assertThat(profile.userId).isEqualTo("u-17")
        assertThat(profile.createdBy).isEqualTo("self-service")
        assertThat(profile.displayName).isEqualTo("Ada King Lovelace")
        assertThat(profile.theme).isEqualTo(Theme.SYSTEM)
        assertThat(profile.tags).containsExactly("beta", "early-adopter")
        assertThat(profile.bio).isNull()
        assertThat(profile.lastLoginIp).isNull()
        assertThat(profile.initials).isEqualTo("AKL")
        assertThat(profile.preferences())
            .containsOnly(entry("newsletter", true), entry("locale", "en-GB"))
    }

    @Test
    fun publishesPublicAndInternalViewsWithComputedAndPreferenceFields() {
        val profile: UserProfile = mapper.readValue(signUp)
        profile.lastLoginIp = "203.0.113.9"

        val internal: JsonNode = mapper.readTree(mapper.writeValueAsString(profile))
        val publicJson: String = mapper.writerWithView(Views.Public::class.java)
            .writeValueAsString(profile)
        val public: JsonNode = mapper.readTree(publicJson)

        assertThat(internal.path("lastLoginIp").asString()).isEqualTo("203.0.113.9")
        assertThat(internal.path("initials").asString()).isEqualTo("AKL")
        assertThat(internal.path("theme").asString()).isEqualTo("SYSTEM")
        assertThat(internal.path("newsletter").asBoolean()).isTrue()
        assertThat(internal.path("locale").asString()).isEqualTo("en-GB")

        assertThat(public.has("lastLoginIp")).isFalse()
        assertThat(public.path("displayName").asString()).isEqualTo("Ada King Lovelace")
        val publicTags: List<String> = public.path("tags").asIterable().map { it.asString() }
        assertThat(publicTags).containsExactly("beta", "early-adopter")
        assertThat(publicJson).startsWith(
            """{"userId":"u-17","createdBy":"self-service","displayName":"Ada King Lovelace",""" +
                """"initials":"AKL","theme":"SYSTEM""""
        )
    }

    @Test
    fun appliesPublicPatchInPlaceWithoutLettingClientsTouchInternalFields() {
        val profile: UserProfile = mapper.readValue(signUp)
        profile.lastLoginIp = "198.51.100.4"
        val patch: String = """
            {
              "bio": "Analyst of engines",
              "theme": "DARK",
              "lastLoginIp": "0.0.0.0",
              "timezone": "Europe/London"
            }
        """.trimIndent()

        val updated: UserProfile = mapper.readerForUpdating(profile)
            .withView(Views.Public::class.java)
            .readValue(patch)

        assertThat(updated).isSameAs(profile)
        assertThat(profile.bio).isEqualTo("Analyst of engines")
        assertThat(profile.theme).isEqualTo(Theme.DARK)
        assertThat(profile.lastLoginIp).isEqualTo("198.51.100.4")
        assertThat(profile.displayName).isEqualTo("Ada King Lovelace")
        assertThat(profile.tags).containsExactly("beta", "early-adopter")
        assertThat(profile.preferences()).containsEntry("timezone", "Europe/London")
        assertThat(profile.preferences()).doesNotContainKey("lastLoginIp")

        val clearTheme: UserProfile = mapper.readerForUpdating(profile)
            .readValue("""{"theme": null, "tags": ["staff"]}""")
        assertThat(clearTheme.theme).isEqualTo(Theme.DARK)
        assertThat(clearTheme.tags).containsExactly("staff")
    }
}
