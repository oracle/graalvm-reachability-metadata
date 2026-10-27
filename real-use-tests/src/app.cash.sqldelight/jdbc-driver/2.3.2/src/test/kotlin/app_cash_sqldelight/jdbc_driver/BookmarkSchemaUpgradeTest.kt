/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package app_cash_sqldelight.jdbc_driver

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.JdbcDriver
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises JDBC schema evolution and DDL execution when upgrading a saved-bookmark database.
 */
@Timeout(60)
class BookmarkSchemaUpgradeTest {
    @Test
    fun upgradesExistingBookmarksAndAcceptsNewFolderAssignments() {
        val dataSource: JdbcDataSource = JdbcDataSource().apply {
            setURL("jdbc:h2:mem:bookmark_schema_upgrade")
        }
        // Keep persisted data alive across the old and upgraded application connections.
        dataSource.connection.use {
            dataSource.asJdbcDriver().use { oldDriver: JdbcDriver ->
                oldDriver.execute(
                    null,
                    """
                    CREATE TABLE bookmarks (
                        id BIGINT PRIMARY KEY,
                        url VARCHAR(300) NOT NULL,
                        title VARCHAR(100) NOT NULL
                    )
                    """.trimIndent(),
                    0
                )
                oldDriver.execute(
                    null,
                    "INSERT INTO bookmarks (id, url, title) VALUES (?, ?, ?)",
                    3
                ) {
                    bindLong(0, 1L)
                    bindString(1, "https://example.com/guide")
                    bindString(2, "Reader's guide")
                }
            }

            dataSource.asJdbcDriver().use { driver: JdbcDriver ->
                upgradeToFolders(driver)
                driver.execute(null, "INSERT INTO folders VALUES ('work', 'Work')", 0)
                val saved: Long = driver.execute(
                    null,
                    "INSERT INTO bookmarks (id, url, label, folder_id) VALUES (?, ?, ?, ?)",
                    4
                ) {
                    bindLong(0, 2L)
                    bindString(1, "https://example.com/project")
                    bindString(2, "Project notes")
                    bindString(3, "work")
                }.value
                assertEquals(1L, saved)
                assertEquals(
                    listOf(
                        Bookmark(1L, "https://example.com/guide", "Reader's guide", "Inbox"),
                        Bookmark(2L, "https://example.com/project", "Project notes", "Work")
                    ),
                    readBookmarks(driver)
                )
            }

            // A later application session sees both the migrated schema and its data.
            dataSource.asJdbcDriver().use { reopened: JdbcDriver ->
                val moved: Long = reopened.execute(
                    null,
                    "UPDATE bookmarks SET folder_id = ? WHERE id = ?",
                    2
                ) {
                    bindString(0, "work")
                    bindLong(1, 1L)
                }.value
                assertEquals(1L, moved)
                assertEquals(
                    listOf(
                        Bookmark(1L, "https://example.com/guide", "Reader's guide", "Work"),
                        Bookmark(2L, "https://example.com/project", "Project notes", "Work")
                    ),
                    readBookmarks(reopened)
                )
            }
        }
    }

    private fun upgradeToFolders(driver: JdbcDriver) {
        driver.execute(
            null,
            "CREATE TABLE folders (id VARCHAR(40) PRIMARY KEY, name VARCHAR(100) NOT NULL)",
            0
        )
        driver.execute(null, "INSERT INTO folders VALUES ('inbox', 'Inbox')", 0)
        driver.execute(null, "ALTER TABLE bookmarks RENAME COLUMN title TO label", 0)
        driver.execute(
            null,
            "ALTER TABLE bookmarks ADD COLUMN folder_id VARCHAR(40) DEFAULT 'inbox' NOT NULL",
            0
        )
        driver.execute(
            null,
            "ALTER TABLE bookmarks ADD CONSTRAINT bookmark_folder FOREIGN KEY (folder_id) REFERENCES folders(id)",
            0
        )
        driver.execute(null, "CREATE INDEX bookmarks_by_folder ON bookmarks(folder_id)", 0)
    }

    private fun readBookmarks(driver: JdbcDriver): List<Bookmark> = driver.executeQuery(
        null,
        """
        SELECT b.id, b.url, b.label, f.name
        FROM bookmarks b JOIN folders f ON b.folder_id = f.id
        ORDER BY b.id
        """.trimIndent(),
        mapper = { cursor ->
            val bookmarks: MutableList<Bookmark> = mutableListOf()
            while (cursor.next().value) {
                bookmarks.add(
                    Bookmark(
                        requireNotNull(cursor.getLong(0)),
                        requireNotNull(cursor.getString(1)),
                        requireNotNull(cursor.getString(2)),
                        requireNotNull(cursor.getString(3))
                    )
                )
            }
            QueryResult.Value(bookmarks.toList())
        },
        parameters = 0
    ).value

    private data class Bookmark(val id: Long, val url: String, val label: String, val folder: String)
}
