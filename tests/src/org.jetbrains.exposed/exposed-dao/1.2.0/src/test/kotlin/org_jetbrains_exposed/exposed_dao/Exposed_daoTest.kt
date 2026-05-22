/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_exposed.exposed_dao

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.EntityChange
import org.jetbrains.exposed.v1.dao.EntityChangeType
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.dao.UuidEntityClass
import org.jetbrains.exposed.v1.dao.exceptions.EntityNotFoundException
import org.jetbrains.exposed.v1.dao.withHook
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.SizedCollection
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import java.util.UUID as JavaUUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

public class ExposedDaoTest {
    @Test
    public fun entityClassCreatesFindsUpdatesDeletesAndReportsLifecycleChanges(): Unit {
        withDatabase(DaoCities, DaoUsers, DaoProfiles) { database: Database ->
            val changes: MutableList<EntityChange> = mutableListOf()

            withHook({ change: EntityChange -> changes += change }) {
                val createdId: Int = transaction(database) {
                    val helsinki: DaoCity = DaoCity.new { name = "Helsinki" }
                    val ada: DaoUser = DaoUser.new {
                        name = "Ada"
                        active = true
                        city = helsinki
                    }
                    DaoProfile.new {
                        user = ada
                        biography = "database researcher"
                    }
                    ada.id.value
                }

                transaction(database) {
                    val ada: DaoUser = DaoUser[createdId]
                    assertThat(ada.name).isEqualTo("Ada")
                    assertThat(ada.city.name).isEqualTo("Helsinki")
                    assertThat(ada.profile!!.biography).isEqualTo("database researcher")

                    val sameAda: DaoUser = DaoUser.findById(createdId)!!
                    assertThat(sameAda).isSameAs(ada)
                    assertThat(DaoUser.count()).isEqualTo(1L)
                    assertThat(DaoUser.find { DaoUsers.name eq "Ada" }.single().id).isEqualTo(ada.id)

                    val updated: DaoUser = DaoUser.findByIdAndUpdate(createdId) { user: DaoUser ->
                        user.name = "Ada Lovelace"
                    }!!
                    assertThat(updated.name).isEqualTo("Ada Lovelace")

                    val singleUpdated: DaoUser = DaoUser.findSingleByAndUpdate(DaoUsers.name eq "Ada Lovelace") { user: DaoUser ->
                        user.active = false
                    }!!
                    assertThat(singleUpdated.active).isFalse()
                }

                transaction(database) {
                    val inactiveViewNames: List<String> = DaoUser.view { DaoUsers.active eq false }
                        .orderBy(DaoUsers.name to SortOrder.ASC)
                        .map { user: DaoUser -> user.name }
                    assertThat(inactiveViewNames).containsExactly("Ada Lovelace")

                    val user: DaoUser = DaoUser[createdId]
                    user.profile!!.delete()
                    user.delete()
                }

                transaction(database) {
                    assertThat(DaoUser.findById(createdId)).isNull()
                    assertThatThrownBy { DaoUser[createdId] }
                        .isInstanceOf(EntityNotFoundException::class.java)
                        .hasMessageContaining("DaoUser")
                }
            }

            assertThat(changes.map { change: EntityChange -> change.changeType })
                .contains(EntityChangeType.Created, EntityChangeType.Updated, EntityChangeType.Removed)
            assertThat(changes.map { change: EntityChange -> change.entityClass })
                .contains(DaoUser, DaoCity, DaoProfile)
        }
    }

    @Test
    public fun referencesReferrersOptionalReferencesAndManyToManyLinksWorkTogether(): Unit {
        withDatabase(DaoCities, DaoUsers, DaoProfiles, DaoTasks, DaoTags, DaoTaskTags) { database: Database ->
            transaction(database) {
                val london: DaoCity = DaoCity.new { name = "London" }
                val mentor: DaoUser = DaoUser.new {
                    name = "Grace"
                    active = true
                    city = london
                }
                val developer: DaoUser = DaoUser.new {
                    name = "Katherine"
                    active = true
                    city = london
                    this.mentor = mentor
                }
                DaoProfile.new {
                    user = developer
                    biography = "orbital mechanics"
                }
                val bug: DaoTag = DaoTag.new { label = "bug" }
                val urgent: DaoTag = DaoTag.new { label = "urgent" }
                val task: DaoTask = DaoTask.new {
                    title = "calculate trajectory"
                    owner = developer
                }
                task.tags = SizedCollection(listOf(urgent, bug))
            }

            transaction(database) {
                val city: DaoCity = DaoCity.find { DaoCities.name eq "London" }.single()
                assertThat(city.users.orderBy(DaoUsers.name to SortOrder.ASC).map { user: DaoUser -> user.name })
                    .containsExactly("Grace", "Katherine")

                val developer: DaoUser = DaoUser.find { DaoUsers.name eq "Katherine" }.single()
                assertThat(developer.city).isEqualTo(city)
                assertThat(developer.mentor!!.name).isEqualTo("Grace")
                assertThat(developer.profile!!.biography).isEqualTo("orbital mechanics")
                assertThat(developer.tasks.map { task: DaoTask -> task.title }).containsExactly("calculate trajectory")

                val task: DaoTask = developer.tasks.single()
                assertThat(task.owner).isEqualTo(developer)
                assertThat(task.tags.map { tag: DaoTag -> tag.label }.sorted()).containsExactly("bug", "urgent")

                val bug: DaoTag = DaoTag.find { DaoTags.label eq "bug" }.single()
                assertThat(bug.tasks.map { linkedTask: DaoTask -> linkedTask.title }).containsExactly("calculate trajectory")
            }
        }
    }

    @Test
    @OptIn(ExperimentalUuidApi::class)
    public fun longAndUuidEntityClassesPersistExplicitIdentifiers(): Unit {
        withDatabase(DaoAuditEvents, DaoApiKeys) { database: Database ->
            val keyId: Uuid = Uuid.random()

            transaction(database) {
                val event: DaoAuditEvent = DaoAuditEvent.new(10_000L) {
                    message = "user logged in"
                }
                val key: DaoApiKey = DaoApiKey.new(keyId) {
                    label = "primary"
                }

                assertThat(event.id.value).isEqualTo(10_000L)
                assertThat(key.id.value).isEqualTo(keyId)
            }

            transaction(database) {
                assertThat(DaoAuditEvent.findById(10_000L)!!.message).isEqualTo("user logged in")
                assertThat(DaoApiKey.findById(keyId)!!.label).isEqualTo("primary")
                assertThat(DaoAuditEvent.all().map { event: DaoAuditEvent -> event.message })
                    .containsExactly("user logged in")
                assertThat(DaoApiKey.count(DaoApiKeys.label eq "primary")).isEqualTo(1L)
            }
        }
    }

    @Test
    public fun entityCacheReloadAndForIdsExposeConsistentEntities(): Unit {
        withDatabase(DaoCities, DaoUsers) { database: Database ->
            val userId: Int = transaction(database) {
                val city: DaoCity = DaoCity.new { name = "Paris" }
                DaoUser.new {
                    name = "Marie"
                    active = true
                    this.city = city
                }.id.value
            }

            transaction(database) {
                val cached: DaoUser = DaoUser[userId]
                val foundAgain: DaoUser = DaoUser.findById(userId)!!
                assertThat(foundAgain).isSameAs(cached)

                cached.name = "Marie Curie"
                cached.flush()
                DaoUser.reload(cached)
                assertThat(DaoUser.forIds(listOf(userId)).single().name).isEqualTo("Marie Curie")

                val cachedByCondition: List<DaoUser> = DaoUser.findWithCacheCondition(
                    { id.value == userId },
                    { DaoUsers.name eq "Marie Curie" }
                ).toList()
                assertThat(cachedByCondition.map { user: DaoUser -> user.id.value }).containsExactly(userId)
                assertThat(cachedByCondition.map { user: DaoUser -> user.name }).containsExactly("Marie Curie")
            }
        }
    }
}

private fun <T> withDatabase(vararg tables: Table, block: (Database) -> T): T {
    val dataSource = JdbcDataSource().apply {
        setURL("jdbc:h2:mem:exposed_dao_${JavaUUID.randomUUID()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
        user = "sa"
        password = ""
    }
    val database: Database = Database.connect(dataSource)

    transaction(database) {
        SchemaUtils.create(*tables)
    }

    try {
        return block(database)
    } finally {
        transaction(database) {
            SchemaUtils.drop(*tables.reversedArray())
        }
    }
}

private object DaoCities : IntIdTable("dao_cities") {
    val name = varchar("name", 80).uniqueIndex()
}

public class DaoCity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<DaoCity>(DaoCities)

    var name: String by DaoCities.name
    val users by DaoUser referrersOn DaoUsers.city
}

private object DaoUsers : IntIdTable("dao_users") {
    val name = varchar("name", 80)
    val active = bool("active")
    val city = reference("city_id", DaoCities)
    val mentor = optReference("mentor_id", this)
}

public class DaoUser(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<DaoUser>(DaoUsers)

    var name: String by DaoUsers.name
    var active: Boolean by DaoUsers.active
    var city: DaoCity by DaoCity referencedOn DaoUsers.city
    var mentor: DaoUser? by DaoUser optionalReferencedOn DaoUsers.mentor
    val profile: DaoProfile? by DaoProfile optionalBackReferencedOn DaoProfiles.user
    val tasks by DaoTask referrersOn DaoTasks.owner
}

private object DaoProfiles : IntIdTable("dao_profiles") {
    val user = reference("user_id", DaoUsers).uniqueIndex()
    val biography = varchar("biography", 256)
}

public class DaoProfile(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<DaoProfile>(DaoProfiles)

    var user: DaoUser by DaoUser referencedOn DaoProfiles.user
    var biography: String by DaoProfiles.biography
}

private object DaoTasks : IntIdTable("dao_tasks") {
    val title = varchar("title", 120)
    val owner = reference("owner_id", DaoUsers)
}

public class DaoTask(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<DaoTask>(DaoTasks)

    var title: String by DaoTasks.title
    var owner: DaoUser by DaoUser referencedOn DaoTasks.owner
    var tags by DaoTag via DaoTaskTags
}

private object DaoTags : IntIdTable("dao_tags") {
    val label = varchar("label", 64).uniqueIndex()
}

public class DaoTag(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<DaoTag>(DaoTags)

    var label: String by DaoTags.label
    var tasks by DaoTask via DaoTaskTags
}

private object DaoTaskTags : Table("dao_task_tags") {
    val task = reference("task_id", DaoTasks)
    val tag = reference("tag_id", DaoTags)

    override val primaryKey = PrimaryKey(task, tag)
}

private object DaoAuditEvents : LongIdTable("dao_audit_events") {
    val message = varchar("message", 160)
}

public class DaoAuditEvent(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<DaoAuditEvent>(DaoAuditEvents)

    var message: String by DaoAuditEvents.message
}

@OptIn(ExperimentalUuidApi::class)
private object DaoApiKeys : UuidTable("dao_api_keys") {
    val label = varchar("label", 80)
}

@OptIn(ExperimentalUuidApi::class)
public class DaoApiKey(id: EntityID<Uuid>) : UuidEntity(id) {
    companion object : UuidEntityClass<DaoApiKey>(DaoApiKeys)

    var label: String by DaoApiKeys.label
}
