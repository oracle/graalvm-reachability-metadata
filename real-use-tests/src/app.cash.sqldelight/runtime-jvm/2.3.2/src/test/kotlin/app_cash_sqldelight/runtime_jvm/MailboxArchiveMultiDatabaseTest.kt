/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package app_cash_sqldelight.runtime_jvm

import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.SQLException

/**
 * Multi-database applications: a mail client keeps its inbox and its archive in two database
 * files, each behind its own driver and `Transacter`, so a transaction on one database neither
 * joins nor notifies the other, a move between them compensates in `afterRollback` when the
 * archive side fails after the inbox side committed, and a maintenance connection that
 * `ATTACH`es both files moves rows across them in one atomic transaction whose table-key
 * notifications reach only its own listeners.
 *
 * `MailQueries` and `ArchiveQueries` are written the way the SQLDelight compiler emits the
 * queries classes of two separate databases, so the scenario drives the same runtime calls
 * generated databases do.
 */
class MailboxArchiveMultiDatabaseTest {

    @TempDir
    lateinit var dataDir: Path

    private lateinit var mailDriver: SqlDriver
    private lateinit var archiveDriver: SqlDriver
    private lateinit var mail: MailQueries
    private lateinit var archive: ArchiveQueries

    @BeforeEach
    fun openDatabases() {
        mailDriver = JdbcSqliteDriver("jdbc:sqlite:${mailFile()}")
        archiveDriver = JdbcSqliteDriver("jdbc:sqlite:${archiveFile()}")
        MailSchema.create(mailDriver)
        ArchiveSchema.create(archiveDriver)
        mail = MailQueries(mailDriver)
        archive = ArchiveQueries(archiveDriver)
    }

    @AfterEach
    fun closeDatabases() {
        mailDriver.close()
        archiveDriver.close()
    }

    @Test
    fun transactionOnInboxNeitherJoinsNorNotifiesArchive() {
        val inbox: Query<Message> = mail.messages()
        val archived: Query<ArchivedMessage> = archive.archived()
        val inboxChanges = CountingListener()
        val archiveChanges = CountingListener()
        inbox.addListener(inboxChanges)
        archived.addListener(archiveChanges)

        mail.transaction {
            mail.receive(Message(1, "inbox", "Quarterly report"))
            assertThat(mailDriver.currentTransaction()).isNotNull()
            assertThat(archiveDriver.currentTransaction()).isNull()

            // The archive driver runs in auto-commit: this write is durable on its own.
            archive.store(ArchivedMessage(7, "Old invoice", "receipts"))
            rollback()
        }

        assertThat(inbox.executeAsList()).isEmpty()
        assertThat(archived.executeAsList()).containsExactly(ArchivedMessage(7, "Old invoice", "receipts"))
        assertThat(inboxChanges.count).isZero()
        assertThat(archiveChanges.count).isEqualTo(1)
        assertThat(mailDriver.currentTransaction()).isNull()

        inbox.removeListener(inboxChanges)
        archived.removeListener(archiveChanges)
    }

    @Test
    fun moveBetweenDatabasesCompensatesInboxWhenArchiveTransactionFails() {
        val mover = FolderArchiver(mail, archive)
        mail.receive(Message(1, "promotions", "Spring sale"))
        mail.receive(Message(2, "inbox", "Lunch on Friday?"))
        mail.receive(Message(3, "promotions", "Free shipping"))
        val inbox: Query<Message> = mail.messages()
        val inboxChanges = CountingListener()
        inbox.addListener(inboxChanges)

        assertThat(mover.archiveFolder("promotions")).isEqualTo(2)

        assertThat(inbox.executeAsList()).containsExactly(Message(2, "inbox", "Lunch on Friday?"))
        assertThat(archive.archived().executeAsList()).containsExactly(
            ArchivedMessage(1, "Spring sale", "promotions"),
            ArchivedMessage(3, "Free shipping", "promotions"),
        )
        assertThat(archive.batches().executeAsList()).containsExactly(Batch("promotions", 2))
        assertThat(inboxChanges.count).isEqualTo(1)

        mail.receive(Message(4, "promotions", "Last chance"))
        inboxChanges.count = 0

        // The batch row for "promotions" already exists, so the archive transaction fails after
        // the inbox transaction, which runs on another driver, has already committed its delete.
        assertThatThrownBy { mover.archiveFolder("promotions") }.isInstanceOf(SQLException::class.java)

        assertThat(inbox.executeAsList()).containsExactly(
            Message(2, "inbox", "Lunch on Friday?"),
            Message(4, "promotions", "Last chance"),
        )
        assertThat(archive.archived().executeAsList()).extracting<Long> { it.id }.containsExactly(1L, 3L)
        assertThat(archive.batches().executeAsList()).containsExactly(Batch("promotions", 2))
        // One refresh for the committed delete and one for the compensating restore.
        assertThat(inboxChanges.count).isEqualTo(2)
        assertThat(mover.log).containsExactly(
            "archived 2 from promotions",
            "restored 1 to promotions",
        )

        inbox.removeListener(inboxChanges)
    }

    @Test
    fun maintenanceConnectionMovesFolderAtomicallyAcrossBothFiles() {
        mail.receive(Message(1, "newsletters", "Kotlin weekly"))
        mail.receive(Message(2, "receipts", "Train ticket"))
        mail.receive(Message(3, "newsletters", "SQLite release notes"))
        val maintenanceDriver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val maintenance = MaintenanceQueries(maintenanceDriver)
        maintenance.attach(mailFile(), archiveFile())

        val inbox: Query<Message> = mail.messages()
        val ownArchive: Query<ArchivedMessage> = archive.archived()
        val mergedInbox: Query<Message> = maintenance.inbox()
        val mergedArchive: Query<ArchivedMessage> = maintenance.archived()
        val inboxChanges = CountingListener()
        val archiveChanges = CountingListener()
        val mergedInboxChanges = CountingListener()
        val mergedArchiveChanges = CountingListener()
        inbox.addListener(inboxChanges)
        ownArchive.addListener(archiveChanges)
        mergedInbox.addListener(mergedInboxChanges)
        mergedArchive.addListener(mergedArchiveChanges)

        val moved: Long = maintenance.transactionWithResult { maintenance.moveFolder("newsletters") }

        assertThat(moved).isEqualTo(2L)
        // The mail and archive drivers read what the maintenance connection wrote to their files.
        assertThat(inbox.executeAsList()).containsExactly(Message(2, "receipts", "Train ticket"))
        assertThat(ownArchive.executeAsList()).containsExactly(
            ArchivedMessage(1, "Kotlin weekly", "newsletters"),
            ArchivedMessage(3, "SQLite release notes", "newsletters"),
        )
        assertThat(mergedArchive.executeAsList()).isEqualTo(ownArchive.executeAsList())
        assertThat(mergedInboxChanges.count).isEqualTo(1)
        assertThat(mergedArchiveChanges.count).isEqualTo(1)
        // Notifications stay on the driver that emitted them.
        assertThat(inboxChanges.count).isZero()
        assertThat(archiveChanges.count).isZero()

        maintenance.transaction {
            assertThat(maintenance.moveFolder("receipts")).isEqualTo(1L)
            assertThat(mergedInbox.executeAsList()).isEmpty()
            rollback()
        }

        assertThat(inbox.executeAsList()).containsExactly(Message(2, "receipts", "Train ticket"))
        assertThat(ownArchive.executeAsList()).extracting<Long> { it.id }.containsExactly(1L, 3L)
        assertThat(mergedInboxChanges.count).isEqualTo(1)
        assertThat(mergedArchiveChanges.count).isEqualTo(1)

        inbox.removeListener(inboxChanges)
        ownArchive.removeListener(archiveChanges)
        mergedInbox.removeListener(mergedInboxChanges)
        mergedArchive.removeListener(mergedArchiveChanges)
        maintenanceDriver.close()
    }

    private fun mailFile(): String = dataDir.resolve("mail.db").toString()

    private fun archiveFile(): String = dataDir.resolve("archive.db").toString()

    /** One row of the inbox `message` table. */
    data class Message(val id: Long, val folder: String, val subject: String)

    /** One row of the archive `archived_message` table. */
    data class ArchivedMessage(val id: Long, val subject: String, val archivedFrom: String)

    /** One row of the archive `batch` table: each folder may be archived in one batch. */
    data class Batch(val folder: String, val size: Long)

    /** Listener an application view registers to know when to re-run its query. */
    class CountingListener : Query.Listener {
        var count: Int = 0

        override fun queryResultsChanged() {
            count++
        }
    }

    /** Application service moving a folder from the inbox database to the archive database. */
    class FolderArchiver(private val mail: MailQueries, private val archive: ArchiveQueries) {
        val log: MutableList<String> = mutableListOf()

        fun archiveFolder(folder: String): Int = archive.transactionWithResult {
            val moving: List<Message> = mail.messages().executeAsList().filter { it.folder == folder }
            moving.forEach { archive.store(ArchivedMessage(it.id, it.subject, folder)) }
            // A separate driver: this is a top-level transaction that commits right away.
            mail.transaction { moving.forEach { mail.delete(it.id) } }
            afterCommit { log += "archived ${moving.size} from $folder" }
            afterRollback {
                mail.transaction { moving.forEach { mail.receive(it) } }
                log += "restored ${moving.size} to $folder"
            }
            archive.recordBatch(Batch(folder, moving.size.toLong()))
            moving.size
        }
    }

    /** Schema the SQLDelight compiler would generate for the inbox database. */
    object MailSchema {
        fun create(driver: SqlDriver) {
            driver.execute(
                null,
                """
                CREATE TABLE message (
                  id INTEGER NOT NULL PRIMARY KEY,
                  folder TEXT NOT NULL,
                  subject TEXT NOT NULL
                )
                """.trimIndent(),
                0,
            )
        }
    }

    /** Schema the SQLDelight compiler would generate for the archive database. */
    object ArchiveSchema {
        fun create(driver: SqlDriver) {
            driver.execute(
                null,
                """
                CREATE TABLE archived_message (
                  id INTEGER NOT NULL PRIMARY KEY,
                  subject TEXT NOT NULL,
                  archived_from TEXT NOT NULL
                )
                """.trimIndent(),
                0,
            )
            driver.execute(null, "CREATE TABLE batch (folder TEXT NOT NULL PRIMARY KEY, size INTEGER NOT NULL)", 0)
        }
    }

    /** Hand-written equivalent of the queries class generated for the inbox database. */
    class MailQueries(driver: SqlDriver) : TransacterImpl(driver) {

        fun messages(): Query<Message> = Query(
            MESSAGES_ID,
            arrayOf("message"),
            driver,
            "Mail.sq",
            "messages",
            "SELECT id, folder, subject FROM message ORDER BY id",
        ) { cursor: SqlCursor -> Message(cursor.getLong(0)!!, cursor.getString(1)!!, cursor.getString(2)!!) }

        fun receive(message: Message) {
            driver.execute(RECEIVE_ID, "INSERT INTO message (id, folder, subject) VALUES (?, ?, ?)", 3) {
                bindLong(0, message.id)
                bindString(1, message.folder)
                bindString(2, message.subject)
            }
            notifyQueries(RECEIVE_ID) { emit -> emit("message") }
        }

        fun delete(id: Long) {
            driver.execute(DELETE_ID, "DELETE FROM message WHERE id = ?", 1) { bindLong(0, id) }
            notifyQueries(DELETE_ID) { emit -> emit("message") }
        }

        private companion object {
            const val MESSAGES_ID: Int = 1
            const val RECEIVE_ID: Int = 2
            const val DELETE_ID: Int = 3
        }
    }

    /** Hand-written equivalent of the queries class generated for the archive database. */
    class ArchiveQueries(driver: SqlDriver) : TransacterImpl(driver) {

        fun archived(): Query<ArchivedMessage> = Query(
            ARCHIVED_ID,
            arrayOf("archived_message"),
            driver,
            "Archive.sq",
            "archived",
            "SELECT id, subject, archived_from FROM archived_message ORDER BY id",
        ) { cursor: SqlCursor -> archivedMessage(cursor) }

        fun batches(): Query<Batch> = Query(
            BATCHES_ID,
            arrayOf("batch"),
            driver,
            "Archive.sq",
            "batches",
            "SELECT folder, size FROM batch ORDER BY folder",
        ) { cursor: SqlCursor -> Batch(cursor.getString(0)!!, cursor.getLong(1)!!) }

        fun store(message: ArchivedMessage) {
            driver.execute(
                STORE_ID,
                "INSERT INTO archived_message (id, subject, archived_from) VALUES (?, ?, ?)",
                3,
            ) {
                bindLong(0, message.id)
                bindString(1, message.subject)
                bindString(2, message.archivedFrom)
            }
            notifyQueries(STORE_ID) { emit -> emit("archived_message") }
        }

        fun recordBatch(batch: Batch) {
            driver.execute(RECORD_BATCH_ID, "INSERT INTO batch (folder, size) VALUES (?, ?)", 2) {
                bindString(0, batch.folder)
                bindLong(1, batch.size)
            }
            notifyQueries(RECORD_BATCH_ID) { emit -> emit("batch") }
        }

        private companion object {
            const val ARCHIVED_ID: Int = 1
            const val BATCHES_ID: Int = 2
            const val STORE_ID: Int = 3
            const val RECORD_BATCH_ID: Int = 4
        }
    }

    /**
     * Queries of a maintenance connection that attaches both database files, so one transaction
     * spans them.
     */
    class MaintenanceQueries(driver: SqlDriver) : TransacterImpl(driver) {

        fun attach(mailPath: String, archivePath: String) {
            driver.execute(null, "ATTACH DATABASE ? AS mail", 1) { bindString(0, mailPath) }
            driver.execute(null, "ATTACH DATABASE ? AS archive", 1) { bindString(0, archivePath) }
        }

        fun inbox(): Query<Message> = Query(
            INBOX_ID,
            arrayOf("message"),
            driver,
            "Maintenance.sq",
            "inbox",
            "SELECT id, folder, subject FROM mail.message ORDER BY id",
        ) { cursor: SqlCursor -> Message(cursor.getLong(0)!!, cursor.getString(1)!!, cursor.getString(2)!!) }

        fun archived(): Query<ArchivedMessage> = Query(
            ARCHIVED_ID,
            arrayOf("archived_message"),
            driver,
            "Maintenance.sq",
            "archived",
            "SELECT id, subject, archived_from FROM archive.archived_message ORDER BY id",
        ) { cursor: SqlCursor -> archivedMessage(cursor) }

        fun moveFolder(folder: String): Long {
            val copied: Long = driver.execute(
                COPY_ID,
                """
                INSERT INTO archive.archived_message (id, subject, archived_from)
                SELECT id, subject, folder FROM mail.message WHERE folder = ?
                """.trimIndent(),
                1,
            ) { bindString(0, folder) }.value
            val removed: Long = driver.execute(REMOVE_ID, "DELETE FROM mail.message WHERE folder = ?", 1) {
                bindString(0, folder)
            }.value
            check(copied == removed) { "copied $copied but removed $removed from $folder" }
            notifyQueries(REMOVE_ID) { emit ->
                emit("message")
                emit("archived_message")
            }
            return removed
        }

        private companion object {
            const val INBOX_ID: Int = 1
            const val ARCHIVED_ID: Int = 2
            const val COPY_ID: Int = 3
            const val REMOVE_ID: Int = 4
        }
    }

    private companion object {
        fun archivedMessage(cursor: SqlCursor): ArchivedMessage =
            ArchivedMessage(cursor.getLong(0)!!, cursor.getString(1)!!, cursor.getString(2)!!)
    }
}
