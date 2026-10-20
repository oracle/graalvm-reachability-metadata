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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Hierarchical and analytical reporting queries through `JdbcSqliteDriver`: `WITH RECURSIVE` tree
 * walks, window functions and keyset pagination with bound parameters.
 *
 * An HR application stores its org chart as an adjacency list. It walks chains of command and
 * subtrees with recursive common table expressions, detects reporting cycles left by bad edits,
 * ranks salaries and sales with window functions, and pages through the staff directory with a
 * parameterized query whose next page is keyed on the last row of the previous one.
 */
class HierarchicalReportingTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var hr: OrgChartDatabase

    @BeforeEach
    fun openOrgChart() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        hr = OrgChartDatabase(driver)
        hr.createSchema()
        hr.transaction {
            hr.hire(Employee(1, "Ada", null, "exec", 30000))
            hr.hire(Employee(2, "Brook", 1, "eng", 20000))
            hr.hire(Employee(3, "Cyd", 1, "sales", 18000))
            hr.hire(Employee(4, "Dee", 2, "eng", 15000))
            hr.hire(Employee(5, "Eli", 2, "eng", 15000))
            hr.hire(Employee(6, "Fay", 4, "eng", 9000))
            hr.hire(Employee(7, "Gus", 3, "sales", 11000))
            hr.hire(Employee(8, "Hal", 3, "sales", 12000))
            hr.hire(Employee(9, "Ivy", 7, "sales", 7000))
        }
    }

    @AfterEach
    fun closeOrgChart() {
        driver.close()
    }

    @Test
    fun recursiveWalksFollowTheChainOfCommandAndTheSubtree() {
        assertEquals(listOf("Ivy", "Gus", "Cyd", "Ada"), hr.chainOfCommand(9))
        assertEquals(
            listOf(
                TreeNode("Brook", 0),
                TreeNode("Brook/Dee", 1),
                TreeNode("Brook/Dee/Fay", 2),
                TreeNode("Brook/Eli", 1),
            ),
            hr.subtree(2),
        )
        assertEquals(
            listOf(
                Span("Ada", 8, 137000),
                Span("Brook", 3, 59000),
                Span("Cyd", 3, 48000),
                Span("Dee", 1, 24000),
                Span("Gus", 1, 18000),
            ),
            hr.spansOfControl(),
        )
    }

    @Test
    fun reassigningAManagerMovesTheWholeBranch() {
        hr.transaction {
            hr.reassign(7, 2)
        }

        assertEquals(listOf("Ivy", "Gus", "Brook", "Ada"), hr.chainOfCommand(9))
        assertEquals(
            listOf("Brook", "Brook/Dee", "Brook/Dee/Fay", "Brook/Eli", "Brook/Gus", "Brook/Gus/Ivy"),
            hr.subtree(2).map { it.path },
        )
        assertEquals(listOf("Cyd", "Cyd/Hal"), hr.subtree(3).map { it.path })
    }

    @Test
    fun guardedWalksTerminateAndReportReportingCycles() {
        assertTrue(hr.cycleMembers().isEmpty(), "a clean org chart has no reporting cycle")

        hr.reassign(1, 6)

        assertEquals(listOf("Fay", "Dee", "Brook", "Ada"), hr.chainOfCommand(6))
        assertEquals(listOf(1L, 2L, 4L, 6L), hr.cycleMembers())
    }

    @Test
    fun windowFunctionsRankSalariesWithinDepartments() {
        assertEquals(
            listOf(
                TopEarner("eng", "Brook", 20000),
                TopEarner("eng", "Dee", 15000),
                TopEarner("exec", "Ada", 30000),
                TopEarner("sales", "Cyd", 18000),
                TopEarner("sales", "Hal", 12000),
            ),
            hr.topEarnersPerDepartment(2),
        )
        assertEquals(
            listOf(
                SalaryRank("Brook", 1, 1, 0),
                SalaryRank("Dee", 2, 2, 5000),
                SalaryRank("Eli", 2, 2, 5000),
                SalaryRank("Fay", 4, 3, 11000),
            ),
            hr.salaryRanks("eng"),
        )
    }

    @Test
    fun windowFunctionsReportRunningTotalsAndMonthlyLeaders() {
        hr.transaction {
            hr.recordSale(8, "2025-01", 500)
            hr.recordSale(8, "2025-02", 800)
            hr.recordSale(8, "2025-03", 650)
            hr.recordSale(7, "2025-01", 300)
            hr.recordSale(7, "2025-02", 300)
            hr.recordSale(7, "2025-03", 900)
        }

        assertEquals(
            listOf(
                MonthlySales("2025-01", 500, 500, null),
                MonthlySales("2025-02", 800, 1300, 300),
                MonthlySales("2025-03", 650, 1950, -150),
            ),
            hr.salesTrend(8),
        )
        assertEquals(
            listOf(
                MonthlySales("2025-01", 300, 300, null),
                MonthlySales("2025-02", 300, 600, 0),
                MonthlySales("2025-03", 900, 1500, 600),
            ),
            hr.salesTrend(7),
        )
        assertEquals(
            mapOf("2025-01" to "Hal", "2025-02" to "Hal", "2025-03" to "Gus"),
            hr.monthlyLeaders(),
        )
    }

    @Test
    fun keysetPaginationWalksTheDirectoryWithoutGapsOrRepeats() {
        val first: List<Employee> = hr.directoryPage(null, 4).executeAsList()
        val second: List<Employee> = hr.directoryPage(first.last(), 4).executeAsList()
        val third: List<Employee> = hr.directoryPage(second.last(), 4).executeAsList()
        val beyond: List<Employee> = hr.directoryPage(third.last(), 4).executeAsList()

        assertEquals(listOf(1L, 2L, 3L, 4L), first.map { it.id })
        assertEquals(listOf(5L, 8L, 7L, 6L), second.map { it.id })
        assertEquals(listOf(9L), third.map { it.id })
        assertTrue(beyond.isEmpty(), "the page after the last row is empty")

        val earlier: Query<Employee> = hr.directoryPage(first.last(), 4)
        hr.hire(Employee(10, "Jo", 1, "eng", 15000))
        assertEquals(listOf(5L, 10L, 8L, 7L), earlier.executeAsList().map { it.id })
    }

    /** One row of the `employee` table; the root of the chart has no manager. */
    data class Employee(val id: Long, val name: String, val managerId: Long?, val department: String, val salary: Long)

    /** A member of a subtree with its slash-separated path from the subtree root. */
    data class TreeNode(val path: String, val depth: Long)

    /** How many people sit below a manager and what they cost together with the manager. */
    data class Span(val manager: String, val reports: Long, val payroll: Long)

    /** One of the best-paid people in a department. */
    data class TopEarner(val department: String, val name: String, val salary: Long)

    /** A salary ranked within its department, with the gap to the best-paid person there. */
    data class SalaryRank(val name: String, val rank: Long, val denseRank: Long, val belowTop: Long)

    /** One month of a salesperson's sales with the running total and the change to last month. */
    data class MonthlySales(val month: String, val cents: Long, val runningTotal: Long, val change: Long?)

    /** The org chart database, written the way SQLDelight generates a database and its queries. */
    class OrgChartDatabase(driver: SqlDriver) : TransacterImpl(driver) {

        fun createSchema() {
            driver.execute(
                null,
                """
                CREATE TABLE employee (
                  id INTEGER NOT NULL PRIMARY KEY,
                  name TEXT NOT NULL,
                  manager_id INTEGER REFERENCES employee(id),
                  department TEXT NOT NULL,
                  salary INTEGER NOT NULL
                )
                """.trimIndent(),
                0,
            )
            driver.execute(null, "CREATE INDEX employee_manager ON employee(manager_id)", 0)
            driver.execute(
                null,
                """
                CREATE TABLE sale (
                  employee_id INTEGER NOT NULL REFERENCES employee(id),
                  month TEXT NOT NULL,
                  cents INTEGER NOT NULL,
                  PRIMARY KEY (employee_id, month)
                )
                """.trimIndent(),
                0,
            )
        }

        fun hire(employee: Employee) {
            driver.execute(
                null,
                "INSERT INTO employee (id, name, manager_id, department, salary) VALUES (?, ?, ?, ?, ?)",
                5,
            ) {
                bindLong(0, employee.id)
                bindString(1, employee.name)
                bindLong(2, employee.managerId)
                bindString(3, employee.department)
                bindLong(4, employee.salary)
            }
            notifyQueries(HIRE) { emit: (String) -> Unit -> emit("employee") }
        }

        fun reassign(id: Long, managerId: Long) {
            driver.execute(null, "UPDATE employee SET manager_id = ? WHERE id = ?", 2) {
                bindLong(0, managerId)
                bindLong(1, id)
            }
        }

        fun recordSale(employeeId: Long, month: String, cents: Long) {
            driver.execute(null, "INSERT INTO sale (employee_id, month, cents) VALUES (?, ?, ?)", 3) {
                bindLong(0, employeeId)
                bindString(1, month)
                bindLong(2, cents)
            }
        }

        /** Walks upward from an employee, stopping when a manager repeats so cycles terminate. */
        fun chainOfCommand(id: Long): List<String> =
            driver.executeQuery(
                null,
                """
                WITH RECURSIVE chain(id, name, manager_id, depth, visited) AS (
                  SELECT id, name, manager_id, 0, ',' || id || ',' FROM employee WHERE id = ?
                  UNION ALL
                  SELECT e.id, e.name, e.manager_id, c.depth + 1, c.visited || e.id || ','
                  FROM employee e JOIN chain c ON e.id = c.manager_id
                  WHERE instr(c.visited, ',' || e.id || ',') = 0
                )
                SELECT name FROM chain ORDER BY depth
                """.trimIndent(),
                { cursor: SqlCursor -> QueryResult.Value(readStrings(cursor)) },
                1,
            ) {
                bindLong(0, id)
            }.value

        fun subtree(id: Long): List<TreeNode> =
            driver.executeQuery(
                null,
                """
                WITH RECURSIVE tree(id, depth, path) AS (
                  SELECT id, 0, name FROM employee WHERE id = ?
                  UNION ALL
                  SELECT e.id, t.depth + 1, t.path || '/' || e.name
                  FROM employee e JOIN tree t ON e.manager_id = t.id
                )
                SELECT path, depth FROM tree ORDER BY path
                """.trimIndent(),
                { cursor: SqlCursor ->
                    val nodes: MutableList<TreeNode> = mutableListOf()
                    while (cursor.next().value) {
                        nodes += TreeNode(cursor.getString(0)!!, cursor.getLong(1)!!)
                    }
                    QueryResult.Value(nodes)
                },
                1,
            ) {
                bindLong(0, id)
            }.value

        fun spansOfControl(): List<Span> =
            driver.executeQuery(
                null,
                """
                WITH RECURSIVE reach(root, id) AS (
                  SELECT id, id FROM employee
                  UNION ALL
                  SELECT r.root, e.id FROM employee e JOIN reach r ON e.manager_id = r.id
                )
                SELECT m.name, count(*) - 1, sum(s.salary)
                FROM reach r
                JOIN employee m ON m.id = r.root
                JOIN employee s ON s.id = r.id
                GROUP BY r.root
                HAVING count(*) > 1
                ORDER BY r.root
                """.trimIndent(),
                { cursor: SqlCursor ->
                    val spans: MutableList<Span> = mutableListOf()
                    while (cursor.next().value) {
                        spans += Span(cursor.getString(0)!!, cursor.getLong(1)!!, cursor.getLong(2)!!)
                    }
                    QueryResult.Value(spans)
                },
                0,
            ).value

        /** Ids of the employees whose upward walk leads back to themselves. */
        fun cycleMembers(): List<Long> =
            driver.executeQuery(
                null,
                """
                WITH RECURSIVE walk(start, id, visited) AS (
                  SELECT id, manager_id, ',' || id || ',' FROM employee WHERE manager_id IS NOT NULL
                  UNION ALL
                  SELECT w.start, e.manager_id, w.visited || e.id || ','
                  FROM walk w JOIN employee e ON e.id = w.id
                  WHERE e.manager_id IS NOT NULL AND instr(w.visited, ',' || e.id || ',') = 0
                )
                SELECT DISTINCT start FROM walk WHERE id = start ORDER BY start
                """.trimIndent(),
                { cursor: SqlCursor ->
                    val ids: MutableList<Long> = mutableListOf()
                    while (cursor.next().value) {
                        ids += cursor.getLong(0)!!
                    }
                    QueryResult.Value(ids)
                },
                0,
            ).value

        fun topEarnersPerDepartment(perDepartment: Long): List<TopEarner> =
            driver.executeQuery(
                null,
                """
                SELECT department, name, salary FROM (
                  SELECT department, name, salary,
                         row_number() OVER (PARTITION BY department ORDER BY salary DESC, id) AS place
                  FROM employee
                )
                WHERE place <= ?
                ORDER BY department, place
                """.trimIndent(),
                { cursor: SqlCursor ->
                    val earners: MutableList<TopEarner> = mutableListOf()
                    while (cursor.next().value) {
                        earners += TopEarner(cursor.getString(0)!!, cursor.getString(1)!!, cursor.getLong(2)!!)
                    }
                    QueryResult.Value(earners)
                },
                1,
            ) {
                bindLong(0, perDepartment)
            }.value

        fun salaryRanks(department: String): List<SalaryRank> =
            driver.executeQuery(
                null,
                """
                SELECT name, rank() OVER w, dense_rank() OVER w, first_value(salary) OVER w - salary
                FROM employee
                WHERE department = ?
                WINDOW w AS (ORDER BY salary DESC)
                ORDER BY salary DESC, id
                """.trimIndent(),
                { cursor: SqlCursor ->
                    val ranks: MutableList<SalaryRank> = mutableListOf()
                    while (cursor.next().value) {
                        ranks += SalaryRank(
                            cursor.getString(0)!!,
                            cursor.getLong(1)!!,
                            cursor.getLong(2)!!,
                            cursor.getLong(3)!!,
                        )
                    }
                    QueryResult.Value(ranks)
                },
                1,
            ) {
                bindString(0, department)
            }.value

        fun salesTrend(employeeId: Long): List<MonthlySales> =
            driver.executeQuery(
                null,
                """
                SELECT month, cents, sum(cents) OVER w, cents - lag(cents) OVER w
                FROM sale
                WHERE employee_id = ?
                WINDOW w AS (ORDER BY month ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)
                ORDER BY month
                """.trimIndent(),
                { cursor: SqlCursor ->
                    val months: MutableList<MonthlySales> = mutableListOf()
                    while (cursor.next().value) {
                        months += MonthlySales(
                            cursor.getString(0)!!,
                            cursor.getLong(1)!!,
                            cursor.getLong(2)!!,
                            cursor.getLong(3),
                        )
                    }
                    QueryResult.Value(months)
                },
                1,
            ) {
                bindLong(0, employeeId)
            }.value

        fun monthlyLeaders(): Map<String, String> =
            driver.executeQuery(
                null,
                """
                SELECT month, name FROM (
                  SELECT s.month, e.name,
                         row_number() OVER (PARTITION BY s.month ORDER BY s.cents DESC, e.id) AS place
                  FROM sale s JOIN employee e ON e.id = s.employee_id
                )
                WHERE place = 1
                ORDER BY month
                """.trimIndent(),
                { cursor: SqlCursor ->
                    val leaders: MutableMap<String, String> = linkedMapOf()
                    while (cursor.next().value) {
                        leaders[cursor.getString(0)!!] = cursor.getString(1)!!
                    }
                    QueryResult.Value(leaders)
                },
                0,
            ).value

        /** The staff directory by descending salary, resuming after [after] when it is set. */
        fun directoryPage(after: Employee?, size: Long): Query<Employee> = DirectoryPageQuery(after, size)

        private fun readStrings(cursor: SqlCursor): List<String> {
            val values: MutableList<String> = mutableListOf()
            while (cursor.next().value) {
                values += cursor.getString(0)!!
            }
            return values
        }

        private fun mapEmployee(cursor: SqlCursor): Employee =
            Employee(
                cursor.getLong(0)!!,
                cursor.getString(1)!!,
                cursor.getLong(2),
                cursor.getString(3)!!,
                cursor.getLong(4)!!,
            )

        /** A keyset-paginated query shaped like the inner query classes SQLDelight generates. */
        private inner class DirectoryPageQuery(
            private val after: Employee?,
            private val size: Long,
        ) : Query<Employee>({ cursor: SqlCursor -> mapEmployee(cursor) }) {

            override fun addListener(listener: Query.Listener) {
                driver.addListener("employee", listener = listener)
            }

            override fun removeListener(listener: Query.Listener) {
                driver.removeListener("employee", listener = listener)
            }

            override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
                if (after == null) {
                    driver.executeQuery(
                        DIRECTORY_FIRST,
                        "SELECT $COLUMNS FROM employee ORDER BY salary DESC, id LIMIT ?",
                        mapper,
                        1,
                    ) {
                        bindLong(0, size)
                    }
                } else {
                    driver.executeQuery(
                        DIRECTORY_NEXT,
                        """
                        SELECT $COLUMNS FROM employee
                        WHERE salary < ? OR (salary = ? AND id > ?)
                        ORDER BY salary DESC, id
                        LIMIT ?
                        """.trimIndent(),
                        mapper,
                        4,
                    ) {
                        bindLong(0, after.salary)
                        bindLong(1, after.salary)
                        bindLong(2, after.id)
                        bindLong(3, size)
                    }
                }
        }

        private companion object {
            const val COLUMNS: String = "id, name, manager_id, department, salary"
            const val HIRE: Int = 1
            const val DIRECTORY_FIRST: Int = 2
            const val DIRECTORY_NEXT: Int = 3
        }
    }
}
