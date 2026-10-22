/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package app_cash_sqldelight.jdbc_driver

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.JdbcCursor
import app.cash.sqldelight.driver.jdbc.JdbcDriver
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import java.sql.Blob
import java.sql.Clob
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises JDBC large-object cursor consumption for archived message bodies and attachments.
 */
@Timeout(60)
class ArchivedMessageLobTest {
    @Test
    fun restoresLargeBodiesAndAttachmentsWithoutConfusingMissingAndEmptyContent() {
        val dataSource: JdbcDataSource = JdbcDataSource().apply {
            setURL("jdbc:h2:mem:archived_message_lobs")
        }
        dataSource.connection.use {
            dataSource.asJdbcDriver().use { driver: JdbcDriver ->
                driver.execute(
                    null,
                    """
                    CREATE TABLE message_archive (
                        id BIGINT PRIMARY KEY,
                        subject VARCHAR(200) NOT NULL,
                        body CLOB NOT NULL,
                        attachment BLOB
                    )
                    """.trimIndent(),
                    0
                )
                val archive: MessageArchive = MessageArchive(driver)
                val body: String = "Customer's export includes quotes ' and line breaks.\n".repeat(4_000)
                val attachment: ByteArray = ByteArray(192 * 1024) { index -> (index % 256).toByte() }
                archive.save(Message(1L, "Monthly export", body, attachment))
                archive.save(Message(2L, "No attachment", "Text only\nSecond line", null))
                archive.save(Message(3L, "Empty upload", "", byteArrayOf()))

                // LOB streams must be consumed inside the mapper, before JDBC closes the result set.
                val restored: List<Message> = archive.restore()
                assertEquals(listOf(1L, 2L, 3L), restored.map { message -> message.id })
                assertEquals("Monthly export", restored[0].subject)
                assertEquals(body, restored[0].body)
                assertArrayEquals(attachment, restored[0].attachment)
                assertEquals("Text only\nSecond line", restored[1].body)
                assertNull(restored[1].attachment)
                assertEquals("", restored[2].body)
                assertArrayEquals(byteArrayOf(), restored[2].attachment)

                // Replace a large object and remove its attachment, then read through a new request.
                archive.save(Message(1L, "Redacted export", "Attachment removed by request", null))
                val revised: List<Message> = archive.restore()
                assertEquals("Redacted export", revised[0].subject)
                assertEquals("Attachment removed by request", revised[0].body)
                assertNull(revised[0].attachment)
                assertEquals(restored[1], revised[1])
                assertEquals(restored[2].subject, revised[2].subject)
                assertEquals("", revised[2].body)
                assertArrayEquals(byteArrayOf(), revised[2].attachment)
            }
        }
    }

    private class MessageArchive(private val driver: JdbcDriver) {
        fun save(message: Message) {
            val changed: Long = driver.execute(
                null,
                "MERGE INTO message_archive (id, subject, body, attachment) KEY (id) VALUES (?, ?, ?, ?)",
                4
            ) {
                bindLong(0, message.id)
                bindString(1, message.subject)
                bindString(2, message.body)
                bindBytes(3, message.attachment)
            }.value
            check(changed == 1L)
        }

        fun restore(): List<Message> = driver.executeQuery(
            null,
            "SELECT id, subject, body, attachment FROM message_archive ORDER BY id",
            mapper = { cursor ->
                val jdbcCursor: JdbcCursor = cursor as JdbcCursor
                val messages: MutableList<Message> = mutableListOf()
                while (jdbcCursor.next().value) {
                    messages.add(
                        Message(
                            id = requireNotNull(jdbcCursor.getLong(0)),
                            subject = requireNotNull(jdbcCursor.getString(1)),
                            body = readBody(requireNotNull(jdbcCursor.getObject<Clob>(2))),
                            attachment = jdbcCursor.getObject<Blob>(3)?.let { blob -> readAttachment(blob) }
                        )
                    )
                }
                QueryResult.Value(messages.toList())
            },
            parameters = 0
        ).value

        private fun readBody(clob: Clob): String = try {
            clob.characterStream.use { reader -> reader.readText() }
        } finally {
            clob.free()
        }

        private fun readAttachment(blob: Blob): ByteArray = try {
            blob.binaryStream.use { stream -> stream.readBytes() }
        } finally {
            blob.free()
        }
    }

    private data class Message(
        val id: Long,
        val subject: String,
        val body: String,
        val attachment: ByteArray?
    )
}
