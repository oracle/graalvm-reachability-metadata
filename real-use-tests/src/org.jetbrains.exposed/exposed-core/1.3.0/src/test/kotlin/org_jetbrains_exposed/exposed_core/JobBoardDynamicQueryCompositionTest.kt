/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_exposed.exposed_core

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.AbstractQuery
import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.andIfNotNull
import org.jetbrains.exposed.v1.core.compoundOr
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.intLiteral
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.explain
import org.jetbrains.exposed.v1.jdbc.orWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test

/**
 * Dynamic query composition: a job board turns optional search filters into a `WHERE` clause
 * with `andIfNotNull`, `compoundOr` and `not`, then refines independent copies of the base query
 * with `andWhere`, `orWhere`, `adjustWhere` and `adjustSelect`, pages it, tags it with a SQL
 * comment, renders it with `prepareSQL` and asks the database to `EXPLAIN` it.
 */
class JobBoardDynamicQueryCompositionTest {

    object JobPostings : Table("job_postings") {
        val id = integer("id")
        val title = varchar("title", 64)
        val company = varchar("company", 32)
        val city = varchar("city", 32).nullable()
        val remote = bool("remote")
        val seniority = varchar("seniority", 16)
        val salaryMin = integer("salary_min")
        val salaryMax = integer("salary_max")

        override val primaryKey = PrimaryKey(id)
    }

    data class Posting(
        val id: Int,
        val title: String,
        val company: String,
        val city: String?,
        val remote: Boolean,
        val seniority: String,
        val salaryMin: Int,
        val salaryMax: Int,
    )

    /** The search form an applicant fills in; every field left empty does not filter. */
    data class SearchFilter(
        val city: String? = null,
        val minSalary: Int? = null,
        val keywords: List<String> = emptyList(),
        val excludedCompany: String? = null,
    )

    private fun criteriaFor(filter: SearchFilter): Op<Boolean> {
        var criteria: Op<Boolean> = Op.TRUE
            .andIfNotNull(filter.city?.let { JobPostings.city eq it })
            .andIfNotNull(filter.minSalary?.let { JobPostings.salaryMax greaterEq it })
        if (filter.keywords.isNotEmpty()) {
            criteria = criteria and filter.keywords.map { JobPostings.title like "%$it%" }.compoundOr()
        }
        filter.excludedCompany?.let { criteria = criteria and not(JobPostings.company eq it) }
        return criteria
    }

    private fun search(filter: SearchFilter): Query =
        JobPostings.selectAll().where(criteriaFor(filter)).orderBy(JobPostings.id to SortOrder.ASC)

    private fun Query.ids(): List<Int> = map { row -> row[JobPostings.id] }

    @Test
    fun composesSearchQueriesFromOptionalFiltersAndRefinesCopies() {
        val database: Database = Database.connect(
            url = "jdbc:h2:mem:job_board;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        val postings: List<Posting> = listOf(
            Posting(1, "Backend Engineer", "Acme", "Berlin", false, "MID", 60_000, 80_000),
            Posting(2, "Senior Backend Engineer", "Globex", "Berlin", true, "SENIOR", 85_000, 110_000),
            Posting(3, "Frontend Developer", "Acme", "Munich", false, "JUNIOR", 45_000, 55_000),
            Posting(4, "Platform Engineer", "Initech", null, true, "SENIOR", 90_000, 120_000),
            Posting(5, "Data Engineer", "Globex", "Berlin", false, "MID", 65_000, 85_000),
            Posting(6, "Principal Architect", "Umbrella", "Hamburg", false, "PRINCIPAL", 130_000, 160_000),
            Posting(7, "Backend Developer", "Initech", "Berlin", true, "JUNIOR", 40_000, 52_000),
        )

        transaction(database) {
            SchemaUtils.create(JobPostings)
            JobPostings.batchInsert(postings) { posting ->
                this[JobPostings.id] = posting.id
                this[JobPostings.title] = posting.title
                this[JobPostings.company] = posting.company
                this[JobPostings.city] = posting.city
                this[JobPostings.remote] = posting.remote
                this[JobPostings.seniority] = posting.seniority
                this[JobPostings.salaryMin] = posting.salaryMin
                this[JobPostings.salaryMax] = posting.salaryMax
            }
        }

        transaction(database) {
            assertThat(search(SearchFilter()).count()).isEqualTo(7L)
            assertThat(search(SearchFilter(city = "Berlin")).ids()).containsExactly(1, 2, 5, 7)
            assertThat(search(SearchFilter(keywords = listOf("Backend", "Platform"))).ids())
                .containsExactly(1, 2, 4, 7)

            val berlinEngineering = SearchFilter(
                city = "Berlin",
                minSalary = 70_000,
                keywords = listOf("Engineer", "Developer"),
                excludedCompany = "Acme",
            )
            val base: Query = search(berlinEngineering)
            assertThat(base.ids()).containsExactly(2, 5)

            val remoteOnly: Query = base.copy().andWhere { JobPostings.remote eq true }
            val orPrincipal: Query = base.copy().orWhere { JobPostings.seniority eq "PRINCIPAL" }
            assertThat(remoteOnly.ids()).containsExactly(2)
            assertThat(orPrincipal.ids()).containsExactly(2, 5, 6)
            assertThat(base.ids()).containsExactly(2, 5)

            val seniorOnly: Query = base.copy().adjustWhere {
                val seniorFilter: Op<Boolean> = JobPostings.seniority eq "SENIOR"
                this?.let { it and seniorFilter } ?: seniorFilter
            }
            assertThat(seniorOnly.ids()).containsExactly(2)
            assertThat(search(SearchFilter(city = "Hamburg", keywords = listOf("Engineer"))).empty()).isTrue()

            val hiringCompanies: List<String> = JobPostings.selectAll()
                .where(criteriaFor(SearchFilter(city = "Berlin")))
                .adjustSelect { select(JobPostings.company) }
                .withDistinct()
                .orderBy(JobPostings.company to SortOrder.ASC)
                .map { row -> row[JobPostings.company] }
            assertThat(hiringCompanies).containsExactly("Acme", "Globex", "Initech")

            val secondPage: List<Int> = search(SearchFilter()).limit(3).offset(3).ids()
            assertThat(secondPage).containsExactly(4, 5, 6)

            val guaranteedFloor: CustomFunction<Int> =
                CustomFunction("GREATEST", IntegerColumnType(), JobPostings.salaryMin, intLiteral(50_000))
            val offers: Map<Int, Int> = JobPostings
                .select(JobPostings.id, guaranteedFloor)
                .where { JobPostings.remote eq true }
                .orderBy(JobPostings.id to SortOrder.ASC)
                .associate { row -> row[JobPostings.id] to row[guaranteedFloor] }
            assertThat(offers).isEqualTo(mapOf(2 to 85_000, 4 to 90_000, 7 to 50_000))

            val tagged: Query = search(SearchFilter(city = "Berlin"))
                .comment("job-board-search", AbstractQuery.CommentPosition.FRONT)
            val preparedSql: String = tagged.prepareSQL(this, true)
            val inlinedSql: String = tagged.prepareSQL(this, false)
            assertThat(preparedSql).startsWith("/*job-board-search*/").contains("?").doesNotContain("'Berlin'")
            assertThat(inlinedSql).contains("'Berlin'")
            assertThat(tagged.ids()).containsExactly(1, 2, 5, 7)

            val plan: List<String> = explain { search(berlinEngineering) }.map { row -> row.toString() }
            assertThat(plan).isNotEmpty()
            assertThat(plan.joinToString("\n").uppercase()).contains("JOB_POSTINGS")
        }

        transaction(database) {
            SchemaUtils.drop(JobPostings)
        }
    }
}
