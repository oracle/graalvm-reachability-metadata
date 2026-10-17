/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package app_cash_sqldelight.sqlite_driver

import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.sql.SQLException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Spatial search over R*Tree virtual tables through `JdbcSqliteDriver`.
 *
 * A store-locator application keeps store locations in an ordinary table and mirrors them, through
 * triggers, into an R*Tree index with an auxiliary name column. A map screen observes the stores in
 * its viewport through a parameterized `Query`, a "near me" search narrows candidates with a bounding
 * box before filtering by distance, and delivery zones stored as rectangles answer which zones cover
 * an address and how many stores each zone serves. Inverted rectangles are rejected by the index.
 */
class SpatialIndexTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var locator: LocatorDatabase

    @BeforeEach
    fun openLocator() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        locator = LocatorDatabase(driver)
        locator.createSchema()
        locator.transaction {
            locator.openStore(Store(1, "Harbor", 1.0, 1.0))
            locator.openStore(Store(2, "Market", 2.5, 3.0))
            locator.openStore(Store(3, "Station", 4.0, 4.0))
            locator.openStore(Store(4, "Hilltop", 8.0, 7.5))
            locator.openStore(Store(5, "Airport", 12.0, 1.5))
            locator.openStore(Store(6, "Old Town", 3.0, 2.0))
            locator.addZone(10, "Downtown", Box(0.0, 5.0, 0.0, 5.0))
            locator.addZone(11, "Riverside", Box(4.0, 9.0, 3.0, 8.0))
            locator.addZone(12, "Airport belt", Box(10.0, 14.0, 0.0, 3.0))
        }
    }

    @AfterEach
    fun closeLocator() {
        driver.close()
    }

    @Test
    fun viewportQueryReturnsOnlyStoresInsideTheBox() {
        assertEquals(
            listOf("Harbor", "Market", "Old Town", "Station"),
            locator.storesIn(Box(0.0, 5.0, 0.0, 5.0)).executeAsList().map { it.name },
        )
        assertEquals(
            listOf("Market", "Old Town", "Station"),
            locator.storesIn(Box(2.0, 6.0, 1.5, 5.0)).executeAsList().map { it.name },
        )
        assertTrue(
            locator.storesIn(Box(5.5, 7.0, 0.0, 2.0)).executeAsList().isEmpty(),
            "an empty patch of the map has no stores",
        )
        assertEquals("ok", locator.indexIntegrity())
    }

    @Test
    fun relocatingAndClosingStoresUpdatesTheIndexAndNotifiesTheMap() {
        val viewport: Query<Store> = locator.storesIn(Box(0.0, 5.0, 0.0, 5.0))
        val map: CountingListener = CountingListener()
        viewport.addListener(map)

        locator.relocate(4, 3.5, 1.0)
        assertEquals(1, map.changes)
        assertEquals(
            listOf(Store(1, "Harbor", 1.0, 1.0), Store(4, "Hilltop", 3.5, 1.0)),
            viewport.executeAsList().filter { it.name.startsWith("H") },
        )

        locator.closeStore(1)
        viewport.removeListener(map)
        locator.closeStore(2)

        assertEquals(2, map.changes)
        assertEquals(listOf("Hilltop", "Old Town", "Station"), viewport.executeAsList().map { it.name })
        assertEquals(4L, locator.indexedStoreCount())
        assertEquals("ok", locator.indexIntegrity())
    }

    @Test
    fun nearbySearchFiltersBoundingBoxCandidatesByDistance() {
        assertEquals(
            listOf(Nearby("Market", 0.25), Nearby("Old Town", 1.0)),
            locator.storesNear(3.0, 3.0, 1.2),
        )
        assertEquals(
            listOf(Nearby("Market", 0.25), Nearby("Old Town", 1.0), Nearby("Station", 2.0)),
            locator.storesNear(3.0, 3.0, 1.5),
        )

        locator.rename(6, "Old Town Corner")

        assertEquals(listOf("Market", "Old Town Corner"), locator.storesNear(3.0, 3.0, 1.2).map { it.name })
        assertTrue(locator.storesNear(10.0, 6.0, 1.0).isEmpty(), "no store lies within reach")
    }

    @Test
    fun deliveryZonesAnswerCoverageAndOverlapQueries() {
        assertEquals(listOf("Downtown", "Riverside"), locator.zonesCovering(4.5, 4.0))
        assertEquals(listOf("Airport belt"), locator.zonesCovering(12.0, 1.5))
        assertTrue(locator.zonesCovering(9.5, 2.0).isEmpty(), "the gap between zones is not served")
        assertEquals(
            listOf(ZoneCoverage("Airport belt", 1), ZoneCoverage("Downtown", 4), ZoneCoverage("Riverside", 2)),
            locator.storesPerZone(),
        )
        assertEquals(listOf("Downtown", "Riverside"), locator.zonesOverlapping(Box(4.5, 6.0, 4.5, 6.0)))
    }

    @Test
    fun invertedRectangleIsRejectedAndTheZoneIsRolledBack() {
        val failure: SQLException = assertThrows(SQLException::class.java) {
            locator.transaction {
                locator.addZone(13, "Broken", Box(9.0, 2.0, 0.0, 1.0))
            }
        }

        assertTrue(failure.message!!.contains("rtree constraint failed"), failure.message)
        assertEquals(3L, locator.zoneCount())
        assertEquals(listOf("Downtown"), locator.zonesCovering(1.0, 0.5))
    }

    /** One row of the `store` table, located on the city grid in kilometres. */
    data class Store(val id: Long, val name: String, val x: Double, val y: Double)

    /** An axis-aligned rectangle on the city grid. */
    data class Box(val minX: Double, val maxX: Double, val minY: Double, val maxY: Double)

    /** A store found near a point, with its squared distance to that point. */
    data class Nearby(val name: String, val distanceSquared: Double)

    /** How many stores lie inside a delivery zone. */
    data class ZoneCoverage(val zone: String, val stores: Long)

    /** Counts how often an observed query was told that its results changed. */
    class CountingListener : Query.Listener {
        var changes: Int = 0
            private set

        override fun queryResultsChanged() {
            changes++
        }
    }

    /** The locator database, written the way SQLDelight generates a database and its queries. */
    class LocatorDatabase(driver: SqlDriver) : TransacterImpl(driver) {

        fun createSchema() {
            driver.execute(
                null,
                "CREATE TABLE store (id INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL, x REAL NOT NULL, y REAL NOT NULL)",
                0,
            )
            driver.execute(
                null,
                "CREATE VIRTUAL TABLE store_index USING rtree(id, min_x, max_x, min_y, max_y, +name TEXT)",
                0,
            )
            driver.execute(
                null,
                """
                CREATE TRIGGER store_indexed AFTER INSERT ON store BEGIN
                  INSERT INTO store_index (id, min_x, max_x, min_y, max_y, name)
                  VALUES (new.id, new.x, new.x, new.y, new.y, new.name);
                END
                """.trimIndent(),
                0,
            )
            driver.execute(
                null,
                """
                CREATE TRIGGER store_reindexed AFTER UPDATE ON store BEGIN
                  UPDATE store_index
                  SET min_x = new.x, max_x = new.x, min_y = new.y, max_y = new.y, name = new.name
                  WHERE id = new.id;
                END
                """.trimIndent(),
                0,
            )
            driver.execute(
                null,
                "CREATE TRIGGER store_unindexed AFTER DELETE ON store BEGIN DELETE FROM store_index WHERE id = old.id; END",
                0,
            )
            driver.execute(null, "CREATE TABLE zone (id INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL)", 0)
            driver.execute(null, "CREATE VIRTUAL TABLE zone_index USING rtree(id, min_x, max_x, min_y, max_y)", 0)
        }

        fun openStore(store: Store) {
            driver.execute(OPEN_STORE, "INSERT INTO store (id, name, x, y) VALUES (?, ?, ?, ?)", 4) {
                bindLong(0, store.id)
                bindString(1, store.name)
                bindDouble(2, store.x)
                bindDouble(3, store.y)
            }
            notifyQueries(OPEN_STORE) { emit: (String) -> Unit -> emit("store") }
        }

        fun relocate(id: Long, x: Double, y: Double) {
            driver.execute(RELOCATE, "UPDATE store SET x = ?, y = ? WHERE id = ?", 3) {
                bindDouble(0, x)
                bindDouble(1, y)
                bindLong(2, id)
            }
            notifyQueries(RELOCATE) { emit: (String) -> Unit -> emit("store") }
        }

        fun rename(id: Long, name: String) {
            driver.execute(RENAME, "UPDATE store SET name = ? WHERE id = ?", 2) {
                bindString(0, name)
                bindLong(1, id)
            }
            notifyQueries(RENAME) { emit: (String) -> Unit -> emit("store") }
        }

        fun closeStore(id: Long) {
            driver.execute(CLOSE_STORE, "DELETE FROM store WHERE id = ?", 1) {
                bindLong(0, id)
            }
            notifyQueries(CLOSE_STORE) { emit: (String) -> Unit -> emit("store") }
        }

        /** Records a zone and its rectangle; the index rejects a rectangle whose minimum exceeds its maximum. */
        fun addZone(id: Long, name: String, area: Box) {
            driver.execute(null, "INSERT INTO zone (id, name) VALUES (?, ?)", 2) {
                bindLong(0, id)
                bindString(1, name)
            }
            driver.execute(null, "INSERT INTO zone_index (id, min_x, max_x, min_y, max_y) VALUES (?, ?, ?, ?, ?)", 5) {
                bindLong(0, id)
                bindDouble(1, area.minX)
                bindDouble(2, area.maxX)
                bindDouble(3, area.minY)
                bindDouble(4, area.maxY)
            }
        }

        fun storesIn(viewport: Box): Query<Store> = ViewportQuery(viewport)

        /** Narrows candidates to the square around the point, then keeps those within the radius. */
        fun storesNear(x: Double, y: Double, radius: Double): List<Nearby> =
            driver.executeQuery(
                null,
                """
                WITH probe(px, py, r) AS (SELECT ?, ?, ?)
                SELECT s.name, (s.min_x - p.px) * (s.min_x - p.px) + (s.min_y - p.py) * (s.min_y - p.py) AS d2
                FROM probe p, store_index s
                WHERE s.min_x >= p.px - p.r AND s.max_x <= p.px + p.r
                  AND s.min_y >= p.py - p.r AND s.max_y <= p.py + p.r
                  AND d2 <= p.r * p.r
                ORDER BY d2, s.name
                """.trimIndent(),
                { cursor: SqlCursor ->
                    val found: MutableList<Nearby> = mutableListOf()
                    while (cursor.next().value) {
                        found += Nearby(cursor.getString(0)!!, cursor.getDouble(1)!!)
                    }
                    QueryResult.Value(found)
                },
                3,
            ) {
                bindDouble(0, x)
                bindDouble(1, y)
                bindDouble(2, radius)
            }.value

        fun zonesCovering(x: Double, y: Double): List<String> =
            readNames(
                """
                SELECT z.name FROM zone_index i JOIN zone z ON z.id = i.id
                WHERE i.min_x <= ? AND i.max_x >= ? AND i.min_y <= ? AND i.max_y >= ?
                ORDER BY z.name
                """.trimIndent(),
                listOf(x, x, y, y),
            )

        fun zonesOverlapping(area: Box): List<String> =
            readNames(
                """
                SELECT z.name FROM zone_index i JOIN zone z ON z.id = i.id
                WHERE i.min_x <= ? AND i.max_x >= ? AND i.min_y <= ? AND i.max_y >= ?
                ORDER BY z.name
                """.trimIndent(),
                listOf(area.maxX, area.minX, area.maxY, area.minY),
            )

        fun storesPerZone(): List<ZoneCoverage> =
            driver.executeQuery(
                null,
                """
                SELECT z.name, count(s.id)
                FROM zone z
                JOIN zone_index zi ON zi.id = z.id
                LEFT JOIN store_index s
                  ON s.min_x >= zi.min_x AND s.max_x <= zi.max_x AND s.min_y >= zi.min_y AND s.max_y <= zi.max_y
                GROUP BY z.id
                ORDER BY z.name
                """.trimIndent(),
                { cursor: SqlCursor ->
                    val coverage: MutableList<ZoneCoverage> = mutableListOf()
                    while (cursor.next().value) {
                        coverage += ZoneCoverage(cursor.getString(0)!!, cursor.getLong(1)!!)
                    }
                    QueryResult.Value(coverage)
                },
                0,
            ).value

        fun indexedStoreCount(): Long = readLong("SELECT count(*) FROM store_index")

        fun zoneCount(): Long = readLong("SELECT count(*) FROM zone")

        /** Runs SQLite's structural check of the store index; it answers `ok` when the tree is sound. */
        fun indexIntegrity(): String =
            driver.executeQuery(
                null,
                "SELECT rtreecheck('store_index')",
                { cursor: SqlCursor ->
                    cursor.next()
                    QueryResult.Value(cursor.getString(0)!!)
                },
                0,
            ).value

        private fun readLong(sql: String): Long =
            driver.executeQuery(
                null,
                sql,
                { cursor: SqlCursor ->
                    cursor.next()
                    QueryResult.Value(cursor.getLong(0)!!)
                },
                0,
            ).value

        private fun readNames(sql: String, arguments: List<Double>): List<String> =
            driver.executeQuery(
                null,
                sql,
                { cursor: SqlCursor ->
                    val names: MutableList<String> = mutableListOf()
                    while (cursor.next().value) {
                        names += cursor.getString(0)!!
                    }
                    QueryResult.Value(names)
                },
                arguments.size,
            ) {
                arguments.forEachIndexed { index: Int, argument: Double -> bindDouble(index, argument) }
            }.value

        private inner class ViewportQuery(
            private val viewport: Box,
        ) : Query<Store>({ cursor: SqlCursor ->
            Store(cursor.getLong(0)!!, cursor.getString(1)!!, cursor.getDouble(2)!!, cursor.getDouble(3)!!)
        }) {

            override fun addListener(listener: Query.Listener) {
                driver.addListener("store", listener = listener)
            }

            override fun removeListener(listener: Query.Listener) {
                driver.removeListener("store", listener = listener)
            }

            override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
                driver.executeQuery(
                    VIEWPORT,
                    """
                    SELECT s.id, s.name, s.x, s.y
                    FROM store_index i JOIN store s ON s.id = i.id
                    WHERE i.min_x >= ? AND i.max_x <= ? AND i.min_y >= ? AND i.max_y <= ?
                    ORDER BY s.name
                    """.trimIndent(),
                    mapper,
                    4,
                ) {
                    bindDouble(0, viewport.minX)
                    bindDouble(1, viewport.maxX)
                    bindDouble(2, viewport.minY)
                    bindDouble(3, viewport.maxY)
                }
        }

        private companion object {
            const val OPEN_STORE: Int = 1
            const val RELOCATE: Int = 2
            const val RENAME: Int = 3
            const val CLOSE_STORE: Int = 4
            const val VIEWPORT: Int = 5
        }
    }
}
