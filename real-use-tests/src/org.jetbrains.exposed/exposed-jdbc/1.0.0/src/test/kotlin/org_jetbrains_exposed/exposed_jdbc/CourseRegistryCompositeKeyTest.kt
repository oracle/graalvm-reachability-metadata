/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_exposed.exposed_jdbc

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.dao.id.CompositeID
import org.jetbrains.exposed.v1.core.dao.id.CompositeIdTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.dao.CompositeEntity
import org.jetbrains.exposed.v1.dao.CompositeEntityClass
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Test

/**
 * Composite primary keys: a university course registry keys courses by department and course
 * number through `CompositeIdTable` and `CompositeEntity`, references them from enrollments with a
 * multi-column foreign key, and looks up, updates and cascades deletes by `CompositeID`.
 */
class CourseRegistryCompositeKeyTest {

    object Courses : CompositeIdTable("courses") {
        val department = varchar("department", 8).entityId()
        val number = integer("number").entityId()
        val title = varchar("title", 128)
        val credits = integer("credits")

        override val primaryKey = PrimaryKey(department, number)
    }

    object Enrollments : IntIdTable("enrollments") {
        val student = varchar("student", 64)
        val courseDepartment = varchar("course_department", 8)
        val courseNumber = integer("course_number")
        val grade = varchar("grade", 2).nullable()

        init {
            foreignKey(
                courseDepartment,
                courseNumber,
                target = Courses.primaryKey,
                onDelete = ReferenceOption.CASCADE,
            )
            uniqueIndex(student, courseDepartment, courseNumber)
        }
    }

    class Course(id: EntityID<CompositeID>) : CompositeEntity(id) {
        companion object : CompositeEntityClass<Course>(Courses)

        var title by Courses.title
        var credits by Courses.credits
        val enrollments by Enrollment referrersOn Enrollments

        val code: String
            get() = "${id.value[Courses.department].value}-${id.value[Courses.number].value}"
    }

    class Enrollment(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<Enrollment>(Enrollments)

        var student by Enrollments.student
        var grade by Enrollments.grade
        var course by Course referencedOn Enrollments
    }

    private fun courseKey(department: String, number: Int): CompositeID = CompositeID {
        it[Courses.department] = department
        it[Courses.number] = number
    }

    @Test
    fun registryKeysCoursesByDepartmentAndNumber() {
        val database = Database.connect("jdbc:h2:mem:registry;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        val compilers: CompositeID = courseKey("CS", 401)
        val algorithms: CompositeID = courseKey("CS", 210)
        val topology: CompositeID = courseKey("MATH", 401)

        transaction(database) {
            SchemaUtils.create(Courses, Enrollments)

            val compilerCourse: Course = Course.new(compilers) {
                title = "Compiler Construction"
                credits = 6
            }
            Course.new(algorithms) {
                title = "Algorithms and Data Structures"
                credits = 5
            }
            Courses.insert {
                it[department] = "MATH"
                it[number] = 401
                it[title] = "General Topology"
                it[credits] = 4
            }

            Enrollment.new {
                student = "ada"
                course = compilerCourse
            }
            Enrollment.new {
                student = "grace"
                course = compilerCourse
                grade = "A"
            }
            Enrollment.new {
                student = "ada"
                course = Course[algorithms]
                grade = "B"
            }
            Enrollment.new {
                student = "emmy"
                course = Course[topology]
            }
        }

        transaction(database) {
            val compilerCourse: Course = Course[compilers]
            assertThat(compilerCourse.title).isEqualTo("Compiler Construction")
            assertThat(compilerCourse.code).isEqualTo("CS-401")
            assertThat(Course.findById(courseKey("CS", 999))).isNull()
            assertThat(Course.findById(topology)!!.title).isEqualTo("General Topology")
            assertThat(compilerCourse.enrollments.map { it.student }).containsExactlyInAnyOrder("ada", "grace")

            val adaCourses: List<String> = Enrollment.find { Enrollments.student eq "ada" }
                .map { it.course.code }
                .sorted()
            assertThat(adaCourses).containsExactly("CS-210", "CS-401")

            val sameNumber: List<String> = Course.find { Courses.number eq 401 }
                .orderBy(Courses.department to SortOrder.ASC)
                .map { it.code }
            assertThat(sameNumber).containsExactly("CS-401", "MATH-401")

            val byId: List<String> = Courses.selectAll()
                .where { Courses.id eq topology }
                .map { it[Courses.title] }
            assertThat(byId).containsExactly("General Topology")

            val rowId: CompositeID = Courses.selectAll()
                .where { (Courses.department eq "CS") and (Courses.credits greater 5) }
                .single()[Courses.id].value
            assertThat(rowId).isEqualTo(compilers)

            val enrollmentCount = Enrollments.id.count()
            val rosterSizes: Map<String, Long> = Enrollments
                .select(Enrollments.courseDepartment, Enrollments.courseNumber, enrollmentCount)
                .groupBy(Enrollments.courseDepartment, Enrollments.courseNumber)
                .associate { "${it[Enrollments.courseDepartment]}-${it[Enrollments.courseNumber]}" to it[enrollmentCount] }
            assertThat(rosterSizes).containsExactlyInAnyOrderEntriesOf(
                mapOf("CS-401" to 2L, "CS-210" to 1L, "MATH-401" to 1L),
            )
        }

        assertThatThrownBy {
            transaction(database) {
                Course.new(courseKey("CS", 210)) {
                    title = "Duplicate Algorithms"
                    credits = 1
                }
            }
        }.isInstanceOf(ExposedSQLException::class.java)

        assertThatThrownBy {
            transaction(database) {
                Enrollments.insert {
                    it[student] = "alan"
                    it[courseDepartment] = "PHYS"
                    it[courseNumber] = 101
                }
            }
        }.isInstanceOf(ExposedSQLException::class.java)

        transaction(database) {
            Course[compilers].credits = 7
            val renamed: Int = Courses.update({ Courses.id eq algorithms }) {
                it[title] = "Algorithms"
            }
            assertThat(renamed).isEqualTo(1)
        }

        transaction(database) {
            assertThat(Course[compilers].credits).isEqualTo(7)
            assertThat(Course[algorithms].title).isEqualTo("Algorithms")
            assertThat(Course[topology].credits).isEqualTo(4)

            val removed: Int = Courses.deleteWhere { Courses.id eq compilers }
            assertThat(removed).isEqualTo(1)
        }

        transaction(database) {
            assertThat(Course.count()).isEqualTo(2L)
            assertThat(Enrollment.all().map { "${it.student}@${it.course.code}" })
                .containsExactlyInAnyOrder("ada@CS-210", "emmy@MATH-401")
            SchemaUtils.drop(Enrollments, Courses)
        }
    }
}
