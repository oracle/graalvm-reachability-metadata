/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_exposed.exposed_jdbc

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.WindowFrameBound
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.case
import org.jetbrains.exposed.v1.core.denseRank
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.intLiteral
import org.jetbrains.exposed.v1.core.lag
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.min
import org.jetbrains.exposed.v1.core.notExists
import org.jetbrains.exposed.v1.core.rank
import org.jetbrains.exposed.v1.core.rowNumber
import org.jetbrains.exposed.v1.core.stringLiteral
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.core.wrapAsExpression
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test

/**
 * Window functions, conditional expressions and subqueries: a running club ranks weekly parkrun
 * finishes per club with `rank`, `denseRank` and `rowNumber`, tracks progress with `lag` and a
 * framed running `sum`, grades finishes with `CASE`, and filters runners through `IN`, `EXISTS`,
 * scalar and aliased subqueries.
 */
class RunningClubLeaderboardWindowFunctionsTest {

    object Runners : Table("runners") {
        val id = integer("id").autoIncrement()
        val name = varchar("name", 32)
        val club = varchar("club", 32)

        override val primaryKey = PrimaryKey(id)
    }

    object Finishes : Table("finishes") {
        val runner = reference("runner_id", Runners.id, onDelete = ReferenceOption.CASCADE)
        val week = integer("week")
        val seconds = integer("seconds")

        override val primaryKey = PrimaryKey(runner, week)
    }

    data class Placing(val name: String, val rank: Long, val denseRank: Long, val position: Long)

    data class Progress(val week: Int, val seconds: Int, val previous: Int?, val totalSeconds: Long)

    @Test
    fun ranksAndFiltersClubFinishesWithAnalyticQueries() {
        val database = Database.connect(
            url = "jdbc:h2:mem:running_club;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )

        transaction(database) {
            SchemaUtils.create(Runners, Finishes)

            val times: Map<Pair<String, String>, List<Int>> = linkedMapOf(
                ("Ada" to "Harbour") to listOf(1500, 1470, 1440),
                ("Ben" to "Harbour") to listOf(1620, 1600),
                ("Cleo" to "Harbour") to listOf(1500, 1530, 1510),
                ("Dev" to "Hill") to listOf(1380, 1400, 1460),
                ("Eli" to "Hill") to listOf(1700, 1650),
            )
            times.forEach { (runner, weekly) ->
                val runnerId: Int = Runners.insert {
                    it[name] = runner.first
                    it[club] = runner.second
                } get Runners.id
                Finishes.batchInsert(weekly.withIndex()) { (index, seconds) ->
                    this[Finishes.runner] = runnerId
                    this[Finishes.week] = index + 1
                    this[Finishes.seconds] = seconds
                }
            }
        }

        transaction(database) {
            val clubRank = rank().over().partitionBy(Runners.club).orderBy(Finishes.seconds)
            val clubDenseRank = denseRank().over().partitionBy(Runners.club).orderBy(Finishes.seconds)
            val position = rowNumber().over()
                .partitionBy(Runners.club)
                .orderBy(Finishes.seconds to SortOrder.ASC, Runners.name to SortOrder.ASC)

            val weekOne: Map<String, List<Placing>> = Runners.innerJoin(Finishes)
                .select(Runners.club, Runners.name, clubRank, clubDenseRank, position)
                .where { Finishes.week eq 1 }
                .orderBy(Runners.club to SortOrder.ASC, position to SortOrder.ASC)
                .groupBy({ it[Runners.club] }) {
                    Placing(it[Runners.name], it[clubRank], it[clubDenseRank], it[position])
                }

            assertThat(weekOne.getValue("Harbour")).containsExactly(
                Placing("Ada", 1, 1, 1),
                Placing("Cleo", 1, 1, 2),
                Placing("Ben", 3, 2, 3),
            )
            assertThat(weekOne.getValue("Hill")).containsExactly(
                Placing("Dev", 1, 1, 1),
                Placing("Eli", 2, 2, 2),
            )
        }

        transaction(database) {
            val previous = Finishes.seconds.lag().over().partitionBy(Finishes.runner).orderBy(Finishes.week)
            val runningTotal = Finishes.seconds.sum().over()
                .partitionBy(Finishes.runner)
                .orderBy(Finishes.week)
                .rows(WindowFrameBound.unboundedPreceding(), WindowFrameBound.currentRow())

            val adaProgress: List<Progress> = Runners.innerJoin(Finishes)
                .select(Finishes.week, Finishes.seconds, previous, runningTotal)
                .where { Runners.name eq "Ada" }
                .orderBy(Finishes.week)
                .map {
                    Progress(
                        it[Finishes.week],
                        it[Finishes.seconds],
                        it.getOrNull(previous),
                        it[runningTotal]!!.toLong(),
                    )
                }

            assertThat(adaProgress).containsExactly(
                Progress(1, 1500, null, 1500),
                Progress(2, 1470, 1500, 2970),
                Progress(3, 1440, 1470, 4410),
            )
            assertThat(adaProgress.mapNotNull { p -> p.previous?.let { it - p.seconds } }).containsExactly(30, 30)
        }

        transaction(database) {
            val tier = case()
                .When(Finishes.seconds less 1450, stringLiteral("gold"))
                .When(Finishes.seconds less 1500, stringLiteral("silver"))
                .Else(stringLiteral("bronze"))
                .alias("tier")

            val weekThreeTiers: Map<String, String> = Runners.innerJoin(Finishes)
                .select(Runners.name, tier)
                .where { Finishes.week eq 3 }
                .associate { it[Runners.name] to it[tier] }
            assertThat(weekThreeTiers).containsExactlyInAnyOrderEntriesOf(
                mapOf("Ada" to "gold", "Cleo" to "bronze", "Dev" to "silver"),
            )

            val goldFinishes = case()
                .When(Finishes.seconds less 1450, intLiteral(1))
                .Else(intLiteral(0))
                .sum()
            val goldPerClub: Map<String, Int?> = Runners.innerJoin(Finishes)
                .select(Runners.club, goldFinishes)
                .groupBy(Runners.club)
                .associate { it[Runners.club] to it[goldFinishes] }
            assertThat(goldPerClub).containsExactlyInAnyOrderEntriesOf(mapOf("Harbour" to 1, "Hill" to 2))
        }

        transaction(database) {
            val racedWeekThree = Finishes.select(Finishes.runner).where { Finishes.week eq 3 }
            assertThat(
                Runners.select(Runners.name)
                    .where { Runners.id inSubQuery racedWeekThree }
                    .orderBy(Runners.name)
                    .map { it[Runners.name] },
            ).containsExactly("Ada", "Cleo", "Dev")

            val missedWeekThree = Runners.select(Runners.name)
                .where {
                    notExists(
                        Finishes.selectAll().where { (Finishes.runner eq Runners.id) and (Finishes.week eq 3) },
                    )
                }
                .orderBy(Runners.name)
                .map { it[Runners.name] }
            assertThat(missedWeekThree).containsExactly("Ben", "Eli")

            val clubsWithGoldFinish = Runners.select(Runners.club)
                .withDistinct()
                .where {
                    exists(
                        Finishes.selectAll().where {
                            (Finishes.runner eq Runners.id) and (Finishes.seconds less 1450)
                        },
                    )
                }
                .map { it[Runners.club] }
            assertThat(clubsWithGoldFinish).containsExactlyInAnyOrder("Harbour", "Hill")

            val courseRecord = wrapAsExpression<Int>(Finishes.select(Finishes.seconds.min()))
            val recordHolder: Pair<String, Int> = Runners.innerJoin(Finishes)
                .select(Runners.name, Finishes.week)
                .where { Finishes.seconds eq courseRecord }
                .single()
                .let { it[Runners.name] to it[Finishes.week] }
            assertThat(recordHolder).isEqualTo("Dev" to 1)

            val best = Finishes.seconds.min().alias("best")
            val personalBests = Finishes.select(Finishes.runner, best).groupBy(Finishes.runner).alias("pb")
            val bestWeeks: Map<String, Pair<Int, Int>> = Runners
                .innerJoin(Finishes)
                .join(personalBests, JoinType.INNER, Finishes.runner, personalBests[Finishes.runner]) {
                    Finishes.seconds eq personalBests[best]
                }
                .select(Runners.name, Finishes.week, Finishes.seconds)
                .associate { it[Runners.name] to (it[Finishes.week] to it[Finishes.seconds]) }
            assertThat(bestWeeks).containsExactlyInAnyOrderEntriesOf(
                mapOf(
                    "Ada" to (3 to 1440),
                    "Ben" to (2 to 1600),
                    "Cleo" to (1 to 1500),
                    "Dev" to (1 to 1380),
                    "Eli" to (2 to 1650),
                ),
            )
        }

        transaction(database) { SchemaUtils.drop(Finishes, Runners) }
    }
}
