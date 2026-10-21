/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_exposed.exposed_core

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.Alias
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.except
import org.jetbrains.exposed.v1.jdbc.intersect
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.union
import org.jetbrains.exposed.v1.jdbc.unionAll
import org.junit.jupiter.api.Test

/**
 * Table aliases and SQL set operations: an org chart resolves managers and skip-level managers
 * through self-joins on aliased tables, and builds staffing rosters with `UNION`, `UNION ALL`,
 * `INTERSECT` and `EXCEPT`.
 */
class OrgChartAliasSetOperationsTest {

    object Employees : Table("employees") {
        val id = integer("id")
        val name = varchar("name", 64)
        val department = varchar("department", 16)
        val managerId = integer("manager_id").references(id).nullable()

        override val primaryKey = PrimaryKey(id)
    }

    object Contractors : Table("contractors") {
        val name = varchar("name", 64)
        val department = varchar("department", 16)

        override val primaryKey = PrimaryKey(name)
    }

    object OnCall : Table("on_call") {
        val name = varchar("name", 64)

        override val primaryKey = PrimaryKey(name)
    }

    data class Person(val id: Int, val name: String, val department: String, val managerId: Int?)

    data class Reporting(val employee: String, val manager: String?, val skipLevel: String?)

    @Test
    fun resolvesReportingLinesAndRostersWithAliasesAndSetOperations() {
        val database: Database = Database.connect(
            url = "jdbc:h2:mem:org_chart;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        val staff: List<Person> = listOf(
            Person(1, "Ada", "EXEC", null),
            Person(2, "Brian", "ENG", 1),
            Person(3, "Chen", "ENG", 2),
            Person(4, "Dana", "ENG", 2),
            Person(5, "Elif", "OPS", 1),
            Person(6, "Femi", "OPS", 5),
        )
        transaction(database) {
            SchemaUtils.create(Employees, Contractors, OnCall)
            Employees.batchInsert(staff) { person ->
                this[Employees.id] = person.id
                this[Employees.name] = person.name
                this[Employees.department] = person.department
                this[Employees.managerId] = person.managerId
            }
            Contractors.batchInsert(listOf("Gus" to "ENG", "Dana" to "ENG", "Hana" to "OPS")) { (name, dept) ->
                this[Contractors.name] = name
                this[Contractors.department] = dept
            }
            OnCall.batchInsert(listOf("Chen", "Femi", "Gus")) { name ->
                this[OnCall.name] = name
            }
        }

        val reporting: List<Reporting> = transaction(database) {
            val managers: Alias<Employees> = Employees.alias("managers")
            val skipLevels: Alias<Employees> = Employees.alias("skip_levels")
            Employees
                .join(managers, JoinType.LEFT, Employees.managerId, managers[Employees.id])
                .join(skipLevels, JoinType.LEFT, managers[Employees.managerId], skipLevels[Employees.id])
                .select(Employees.name, managers[Employees.name], skipLevels[Employees.name])
                .orderBy(Employees.id to SortOrder.ASC)
                .map { row ->
                    Reporting(
                        row[Employees.name],
                        row.getOrNull(managers[Employees.name]),
                        row.getOrNull(skipLevels[Employees.name]),
                    )
                }
        }
        assertThat(reporting).containsExactly(
            Reporting("Ada", null, null),
            Reporting("Brian", "Ada", null),
            Reporting("Chen", "Brian", "Ada"),
            Reporting("Dana", "Brian", "Ada"),
            Reporting("Elif", "Ada", null),
            Reporting("Femi", "Elif", "Ada"),
        )

        val directReportsOfBrian: List<String> = transaction(database) {
            val managers: Alias<Employees> = Employees.alias("managers")
            Employees
                .join(managers, JoinType.INNER, Employees.managerId, managers[Employees.id])
                .select(Employees.name)
                .where { managers[Employees.name] eq "Brian" }
                .orderBy(Employees.name to SortOrder.ASC)
                .map { row -> row[Employees.name] }
        }
        assertThat(directReportsOfBrian).containsExactly("Chen", "Dana")

        transaction(database) {
            val engineers: Query = Employees.select(Employees.name).where { Employees.department eq "ENG" }
            val engineeringContractors: Query = Contractors.select(Contractors.name)
                .where { Contractors.department eq "ENG" }
            val onCall: Query = OnCall.select(OnCall.name)

            // UNION removes the person who is both an employee and a contractor; UNION ALL keeps both rows.
            val engineeringRoster: List<String> = engineers.union(engineeringContractors)
                .map { row -> row[Employees.name] }
                .sorted()
            assertThat(engineeringRoster).containsExactly("Brian", "Chen", "Dana", "Gus")
            val engineeringHeadcount: Long = engineers.unionAll(engineeringContractors).count()
            assertThat(engineeringHeadcount).isEqualTo(5)

            val engineersOnCall: List<String> = engineers.intersect(onCall)
                .map { row -> row[Employees.name] }
            assertThat(engineersOnCall).containsExactly("Chen")

            val engineersOffRota: List<String> = engineers.except(onCall)
                .map { row -> row[Employees.name] }
                .sorted()
            assertThat(engineersOffRota).containsExactly("Brian", "Dana")

            val topLevel: Query = Employees.select(Employees.name).where { Employees.managerId.isNull() }
            val everyoneOnCallOrTopLevel: List<String> = topLevel.union(onCall)
                .map { row -> row[Employees.name] }
                .sorted()
            assertThat(everyoneOnCallOrTopLevel).containsExactly("Ada", "Chen", "Femi", "Gus")
        }

        transaction(database) {
            SchemaUtils.drop(OnCall, Contractors, Employees)
        }
    }
}
