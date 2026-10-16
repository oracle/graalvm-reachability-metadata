/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_exposed.exposed_json

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.json.json
import org.junit.jupiter.api.Test

/**
 * Custom-serializer json columns on DAO entities: an account service maps user profiles to
 * Exposed entities whose `json` columns are encoded and decoded by Jackson lambdas.
 */
class UserPreferencesJacksonEntityTest {

    data class NotificationSettings(val email: Boolean, val push: Boolean, val digestHour: Int?)

    data class Preferences(
        val theme: String,
        val locale: String,
        val notifications: NotificationSettings,
        val pinnedProjects: List<String> = emptyList(),
    )

    data class AuditTrail(val changes: List<String>)

    object Profiles : IntIdTable("user_profiles") {
        private val mapper: ObjectMapper = jacksonObjectMapper()

        val username = varchar("username", 64).uniqueIndex()
        val preferences = json<Preferences>(
            "preferences",
            { mapper.writeValueAsString(it) },
            { mapper.readValue<Preferences>(it) },
        )
        val audit = json<AuditTrail>(
            "audit",
            { mapper.writeValueAsString(it) },
            { mapper.readValue<AuditTrail>(it) },
        ).nullable()
    }

    class Profile(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<Profile>(Profiles)

        var username: String by Profiles.username
        var preferences: Preferences by Profiles.preferences
        var audit: AuditTrail? by Profiles.audit
    }

    @Test
    fun persistsAndEvolvesJacksonEncodedPreferencesThroughEntities() {
        val database = Database.connect(
            url = "jdbc:h2:mem:user_profiles;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        val darkMode = Preferences(
            theme = "dark",
            locale = "en-GB",
            notifications = NotificationSettings(email = true, push = false, digestHour = 8),
            pinnedProjects = listOf("graal", "exposed"),
        )
        val defaults = Preferences(
            theme = "light",
            locale = "de-DE",
            notifications = NotificationSettings(email = false, push = true, digestHour = null),
        )

        val (adaId: Int, linusId: Int) = transaction(database) {
            SchemaUtils.create(Profiles)
            val ada = Profile.new {
                username = "ada"
                preferences = darkMode
            }
            val linus = Profile.new {
                username = "linus"
                preferences = defaults
                audit = AuditTrail(listOf("created"))
            }
            ada.id.value to linus.id.value
        }

        transaction(database) {
            val ada = Profile[adaId]
            assertThat(ada.username).isEqualTo("ada")
            assertThat(ada.preferences).isEqualTo(darkMode)
            assertThat(ada.audit).isNull()

            val linus = Profile.find { Profiles.username eq "linus" }.single()
            assertThat(linus.id.value).isEqualTo(linusId)
            assertThat(linus.preferences.notifications.digestHour).isNull()
            assertThat(linus.audit).isEqualTo(AuditTrail(listOf("created")))
        }

        transaction(database) {
            val ada = Profile[adaId]
            ada.preferences = ada.preferences.copy(
                theme = "high-contrast",
                pinnedProjects = ada.preferences.pinnedProjects + "kotlin",
            )
            ada.audit = AuditTrail(listOf("theme changed"))
        }

        val storedJson: String? = transaction(database) {
            exec("SELECT preferences FROM user_profiles WHERE id = $adaId") { rs ->
                if (rs.next()) rs.getString(1) else null
            }
        }
        assertThat(storedJson).contains("\"theme\":\"high-contrast\"").contains("\"digestHour\":8")

        transaction(database) {
            val ada = Profile[adaId]
            assertThat(ada.preferences.theme).isEqualTo("high-contrast")
            assertThat(ada.preferences.pinnedProjects).containsExactly("graal", "exposed", "kotlin")
            assertThat(ada.preferences.notifications).isEqualTo(darkMode.notifications)
            assertThat(ada.audit?.changes).containsExactly("theme changed")

            val emailSubscribers: List<String> = Profile.all()
                .filter { it.preferences.notifications.email }
                .map { it.username }
            assertThat(emailSubscribers).containsExactly("ada")

            Profile[linusId].delete()
            assertThat(Profile.count()).isEqualTo(1L)
            SchemaUtils.drop(Profiles)
        }
    }
}
