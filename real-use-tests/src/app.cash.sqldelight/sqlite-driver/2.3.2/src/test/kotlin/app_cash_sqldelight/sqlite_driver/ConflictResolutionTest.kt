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
 * Conflict resolution and constraint enforcement on writes through `JdbcSqliteDriver`: upserts,
 * `INSERT OR IGNORE`, `RETURNING` clauses, affected-row counts and constraint-violation errors.
 *
 * A contacts application syncs its address book from a server. It inserts contacts and reads
 * the generated id back with `RETURNING`, merges server revisions with an upsert that never
 * overwrites newer data, tags contacts idempotently, and uses the row counts the driver returns
 * to decide whether observers of the contact list must be told about a change.
 */
class ConflictResolutionTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var book: AddressBook
    private lateinit var contacts: Query<Contact>
    private lateinit var screen: CountingListener

    @BeforeEach
    fun openAddressBook() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        book = AddressBook(driver)
        book.createSchema()
        contacts = book.selectContacts()
        screen = CountingListener()
        contacts.addListener(screen)
    }

    @AfterEach
    fun closeAddressBook() {
        contacts.removeListener(screen)
        driver.close()
    }

    @Test
    fun insertReturnsGeneratedIdsAndRejectsDuplicateEmails() {
        val ada: Long = book.addContact("ada@example.com", "Ada", 1)
        val alan: Long = book.addContact("alan@example.com", "Alan", 1)

        val failure: SQLException = assertThrows(SQLException::class.java) {
            book.addContact("ADA@example.com", "Ada Again", 1)
        }

        assertEquals(1L, ada)
        assertEquals(2L, alan)
        assertEquals(SQLITE_CONSTRAINT, failure.errorCode)
        assertTrue(failure.message!!.contains("UNIQUE constraint failed: contact.email"), failure.message)
        assertEquals(
            listOf(Contact(1, "ada@example.com", "Ada", 1), Contact(2, "alan@example.com", "Alan", 1)),
            contacts.executeAsList(),
        )
        assertEquals(2, screen.changes, "the rejected insert must not notify observers")
    }

    @Test
    fun syncUpsertInsertsUpdatesNewerRevisionsAndSkipsStaleOnes() {
        val inserted: Long = book.sync("grace@example.com", "Grace", 3)
        val updated: Long = book.sync("Grace@Example.com", "Grace Hopper", 4)
        val stale: Long = book.sync("grace@example.com", "G.", 2)
        val sameRevision: Long = book.sync("grace@example.com", "Grace H.", 4)

        assertEquals(listOf(1L, 1L, 0L, 0L), listOf(inserted, updated, stale, sameRevision))
        assertEquals(listOf(Contact(1, "grace@example.com", "Grace Hopper", 4)), contacts.executeAsList())
        assertEquals(2, screen.changes, "only syncs that changed a row may notify observers")
    }

    @Test
    fun insertOrIgnoreTagsAContactOnlyOnce() {
        val id: Long = book.addContact("linus@example.com", "Linus", 1)

        val added: List<Long> = listOf("work", "kernel", "work", "Work").map { tag: String -> book.tag(id, tag) }

        assertEquals(listOf(1L, 1L, 0L, 1L), added)
        assertEquals(listOf("Work", "kernel", "work"), book.tags(id))
    }

    @Test
    fun checkAndNotNullViolationsNameTheFailingConstraint() {
        val zeroRevision: SQLException = assertThrows(SQLException::class.java) {
            book.addContact("ken@example.com", "Ken", 0)
        }
        val missingName: SQLException = assertThrows(SQLException::class.java) {
            book.addContact("dennis@example.com", null, 1)
        }

        assertEquals(SQLITE_CONSTRAINT, zeroRevision.errorCode)
        assertTrue(zeroRevision.message!!.contains("CHECK constraint failed"), zeroRevision.message)
        assertEquals(SQLITE_CONSTRAINT, missingName.errorCode)
        assertTrue(missingName.message!!.contains("NOT NULL constraint failed: contact.name"), missingName.message)
        assertEquals(emptyList<Contact>(), contacts.executeAsList())
    }

    @Test
    fun aConflictInsideAnImportRollsBackTheWholeBatch() {
        book.addContact("barbara@example.com", "Barbara", 1)
        val incoming: List<Pair<String, String>> = listOf(
            "edsger@example.com" to "Edsger",
            "tony@example.com" to "Tony",
            "barbara@example.com" to "Barbara L.",
        )

        assertThrows(SQLException::class.java) {
            book.transaction {
                incoming.forEach { (email: String, name: String) -> book.addContact(email, name, 1) }
            }
        }

        assertEquals(listOf(Contact(1, "barbara@example.com", "Barbara", 1)), contacts.executeAsList())
        assertEquals(1, screen.changes, "the rolled-back import must not notify observers")
    }

    @Test
    fun updateAndDeleteReportTheRowsTheyTouched() {
        book.addContact("ada@example.com", "Ada", 1)
        book.addContact("alan@example.com", "Alan", 1)
        book.addContact("grace@navy.mil", "Grace", 1)

        val favorited: Long = book.favoriteDomain("example.com")
        val unknownDomain: Long = book.favoriteDomain("example.org")
        val removed: List<String> = book.removeFavorites()

        assertEquals(2L, favorited)
        assertEquals(0L, unknownDomain)
        assertEquals(listOf("ada@example.com", "alan@example.com"), removed.sorted())
        assertEquals(listOf(Contact(3, "grace@navy.mil", "Grace", 1)), contacts.executeAsList())
        assertEquals(5, screen.changes, "three inserts, one effective update and one delete")
    }

    /** One row of the `contact` table. */
    data class Contact(val id: Long, val email: String, val name: String, val revision: Long)

    /** Counts how often the observed query reported changed results. */
    class CountingListener : Query.Listener {
        var changes: Int = 0
            private set

        override fun queryResultsChanged() {
            changes++
        }
    }

    /** The address book's database, written the way SQLDelight generates a database and its queries. */
    class AddressBook(driver: SqlDriver) : TransacterImpl(driver) {

        fun createSchema() {
            driver.execute(
                null,
                """
                CREATE TABLE contact (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  email TEXT NOT NULL UNIQUE COLLATE NOCASE,
                  name TEXT NOT NULL,
                  revision INTEGER NOT NULL CHECK (revision > 0),
                  favorite INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
                0,
            )
            driver.execute(
                null,
                """
                CREATE TABLE contact_tag (
                  contact_id INTEGER NOT NULL REFERENCES contact (id),
                  tag TEXT NOT NULL,
                  PRIMARY KEY (contact_id, tag)
                )
                """.trimIndent(),
                0,
            )
        }

        /** Inserts a contact and returns the id SQLite assigned to it. */
        fun addContact(email: String, name: String?, revision: Long): Long {
            val id: Long = driver.executeQuery(
                ADD_CONTACT,
                "INSERT INTO contact (email, name, revision) VALUES (?, ?, ?) RETURNING id",
                { cursor: SqlCursor ->
                    check(cursor.next().value) { "INSERT ... RETURNING produced no row" }
                    QueryResult.Value(cursor.getLong(0)!!)
                },
                3,
            ) {
                bindString(0, email)
                bindString(1, name)
                bindLong(2, revision)
            }.value
            notifyContacts(ADD_CONTACT)
            return id
        }

        /** Merges a contact from the server; a revision that is not newer leaves the row as it is. */
        fun sync(email: String, name: String, revision: Long): Long {
            val changed: Long = driver.execute(
                SYNC,
                """
                INSERT INTO contact (email, name, revision) VALUES (?, ?, ?)
                ON CONFLICT (email) DO UPDATE SET name = excluded.name, revision = excluded.revision
                WHERE excluded.revision > contact.revision
                """.trimIndent(),
                3,
            ) {
                bindString(0, email)
                bindString(1, name)
                bindLong(2, revision)
            }.value
            if (changed > 0) notifyContacts(SYNC)
            return changed
        }

        fun tag(contactId: Long, tag: String): Long =
            driver.execute(TAG, "INSERT OR IGNORE INTO contact_tag (contact_id, tag) VALUES (?, ?)", 2) {
                bindLong(0, contactId)
                bindString(1, tag)
            }.value

        fun tags(contactId: Long): List<String> =
            driver.executeQuery(TAGS, "SELECT tag FROM contact_tag WHERE contact_id = ? ORDER BY tag", { cursor: SqlCursor ->
                val tags: MutableList<String> = mutableListOf()
                while (cursor.next().value) tags += cursor.getString(0)!!
                QueryResult.Value(tags.toList())
            }, 1) {
                bindLong(0, contactId)
            }.value

        fun favoriteDomain(domain: String): Long {
            val changed: Long = driver.execute(
                FAVORITE_DOMAIN,
                "UPDATE contact SET favorite = 1 WHERE email LIKE '%@' || ? AND favorite = 0",
                1,
            ) {
                bindString(0, domain)
            }.value
            if (changed > 0) notifyContacts(FAVORITE_DOMAIN)
            return changed
        }

        /** Deletes every favorite contact and returns the deleted e-mail addresses. */
        fun removeFavorites(): List<String> {
            val removed: List<String> = driver.executeQuery(
                REMOVE_FAVORITES,
                "DELETE FROM contact WHERE favorite = 1 RETURNING email",
                { cursor: SqlCursor ->
                    val emails: MutableList<String> = mutableListOf()
                    while (cursor.next().value) emails += cursor.getString(0)!!
                    QueryResult.Value(emails.toList())
                },
                0,
            ).value
            if (removed.isNotEmpty()) notifyContacts(REMOVE_FAVORITES)
            return removed
        }

        fun selectContacts(): Query<Contact> =
            Query(SELECT_CONTACTS, arrayOf("contact"), driver, "AddressBook.sq", "selectContacts",
                "SELECT id, email, name, revision FROM contact ORDER BY id") { cursor: SqlCursor ->
                Contact(cursor.getLong(0)!!, cursor.getString(1)!!, cursor.getString(2)!!, cursor.getLong(3)!!)
            }

        private fun notifyContacts(identifier: Int) {
            notifyQueries(identifier) { emit: (String) -> Unit -> emit("contact") }
        }

        private companion object {
            const val ADD_CONTACT: Int = 1
            const val SYNC: Int = 2
            const val TAG: Int = 3
            const val TAGS: Int = 4
            const val FAVORITE_DOMAIN: Int = 5
            const val REMOVE_FAVORITES: Int = 6
            const val SELECT_CONTACTS: Int = 7
        }
    }

    private companion object {
        /** SQLite's primary result code for a constraint violation, reported as the JDBC error code. */
        const val SQLITE_CONSTRAINT: Int = 19
    }
}
