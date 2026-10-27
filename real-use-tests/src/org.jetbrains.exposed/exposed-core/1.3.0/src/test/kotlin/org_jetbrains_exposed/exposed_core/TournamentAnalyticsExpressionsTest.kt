/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_exposed.exposed_core

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ExpressionWithColumnTypeAlias
import org.jetbrains.exposed.v1.core.QueryAlias
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.case
import org.jetbrains.exposed.v1.core.coalesce
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.intLiteral
import org.jetbrains.exposed.v1.core.lag
import org.jetbrains.exposed.v1.core.notExists
import org.jetbrains.exposed.v1.core.rowNumber
import org.jetbrains.exposed.v1.core.stringLiteral
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test

/**
 * Analytic query expressions: a tournament leaderboard ranks players with window functions
 * over a derived table, labels scores with `CASE` and `COALESCE`, and filters players with
 * `IN` and `NOT EXISTS` subqueries.
 */
class TournamentAnalyticsExpressionsTest {

    object Players : Table("players") {
        val id: Column<Int> = integer("id")
        val name: Column<String> = varchar("name", 32)
        val team: Column<String> = varchar("team", 16)
        val nickname: Column<String?> = varchar("nickname", 32).nullable()

        override val primaryKey = PrimaryKey(id)
    }

    object RoundScores : Table("round_scores") {
        val playerId: Column<Int> = integer("player_id").references(Players.id)
        val round: Column<Int> = integer("round")
        val points: Column<Int> = integer("points")

        override val primaryKey = PrimaryKey(playerId, round)
    }

    data class Player(val id: Int, val name: String, val team: String, val nickname: String?)

    data class Score(val playerId: Int, val round: Int, val points: Int)

    @Test
    fun buildsLeaderboardWithWindowFunctionsCaseExpressionsAndSubqueries() {
        val database: Database = Database.connect(
            url = "jdbc:h2:mem:tournament_analytics;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        val players: List<Player> = listOf(
            Player(1, "Alice", "red", "Ace"),
            Player(2, "Bob", "red", null),
            Player(3, "Carol", "blue", "Cee"),
            Player(4, "Dave", "blue", null),
            Player(5, "Erin", "green", null),
        )
        val scores: List<Score> = listOf(
            Score(1, 1, 40), Score(1, 2, 55),
            Score(2, 1, 62), Score(2, 2, 30),
            Score(3, 1, 20), Score(3, 2, 35),
            Score(4, 1, 35), Score(4, 2, 70),
        )

        transaction(database) {
            SchemaUtils.create(Players, RoundScores)
            players.forEach { player ->
                Players.insert {
                    it[id] = player.id
                    it[name] = player.name
                    it[team] = player.team
                    it[nickname] = player.nickname
                }
            }
            RoundScores.batchInsert(scores) { score ->
                this[RoundScores.playerId] = score.playerId
                this[RoundScores.round] = score.round
                this[RoundScores.points] = score.points
            }
        }

        val teamBests: List<String> = transaction(database) {
            val teamRank: ExpressionWithColumnTypeAlias<Long> = rowNumber().over()
                .partitionBy(Players.team)
                .orderBy(RoundScores.points to SortOrder.DESC)
                .alias("team_rank")
            val ranked: QueryAlias = (Players innerJoin RoundScores)
                .select(Players.team, Players.name, RoundScores.points, teamRank)
                .alias("ranked")
            ranked.selectAll()
                .where { ranked[teamRank] eq 1L }
                .orderBy(ranked[Players.team] to SortOrder.ASC)
                .map { row -> "${row[ranked[Players.team]]}:${row[ranked[Players.name]]}:${row[ranked[RoundScores.points]]}" }
        }
        assertThat(teamBests).containsExactly("blue:Dave:70", "red:Bob:62")

        val roundDeltas: List<String> = transaction(database) {
            val previousPoints: ExpressionWithColumnTypeAlias<Int?> = RoundScores.points
                .lag(intLiteral(1), intLiteral(0))
                .over()
                .partitionBy(RoundScores.playerId)
                .orderBy(RoundScores.round to SortOrder.ASC)
                .alias("previous_points")
            (Players innerJoin RoundScores)
                .select(Players.name, RoundScores.round, RoundScores.points, previousPoints)
                .orderBy(Players.name to SortOrder.ASC, RoundScores.round to SortOrder.ASC)
                .map { row ->
                    val delta: Int = row[RoundScores.points] - checkNotNull(row[previousPoints])
                    "${row[Players.name]}#${row[RoundScores.round]}:$delta"
                }
        }
        assertThat(roundDeltas).containsExactly(
            "Alice#1:40", "Alice#2:15",
            "Bob#1:62", "Bob#2:-32",
            "Carol#1:20", "Carol#2:15",
            "Dave#1:35", "Dave#2:35",
        )

        val finalRoundMedals: List<String> = transaction(database) {
            val displayName: ExpressionWithColumnTypeAlias<String> =
                coalesce(Players.nickname, Players.name).alias("display_name")
            val medal: ExpressionWithColumnTypeAlias<String> = case()
                .When(RoundScores.points greaterEq 60, stringLiteral("gold"))
                .When(RoundScores.points greaterEq 40, stringLiteral("silver"))
                .Else(stringLiteral("bronze"))
                .alias("medal")
            (Players innerJoin RoundScores)
                .select(displayName, medal, RoundScores.points)
                .where { RoundScores.round eq 2 }
                .orderBy(RoundScores.points to SortOrder.DESC)
                .map { row -> "${row[displayName]}=${row[medal]}" }
        }
        assertThat(finalRoundMedals).containsExactly("Dave=gold", "Ace=silver", "Cee=bronze", "Bob=bronze")

        val (highScorers: List<String>, absentees: List<String>) = transaction(database) {
            val highScorers: List<String> = Players.select(Players.name)
                .where { Players.id inSubQuery RoundScores.select(RoundScores.playerId).where { RoundScores.points greater 60 } }
                .orderBy(Players.name to SortOrder.ASC)
                .map { it[Players.name] }
            val absentees: List<String> = Players.select(Players.name)
                .where { notExists(RoundScores.selectAll().where { RoundScores.playerId eq Players.id }) }
                .map { it[Players.name] }
            highScorers to absentees
        }
        assertThat(highScorers).containsExactly("Bob", "Dave")
        assertThat(absentees).containsExactly("Erin")

        transaction(database) {
            SchemaUtils.drop(RoundScores, Players)
        }
    }
}
