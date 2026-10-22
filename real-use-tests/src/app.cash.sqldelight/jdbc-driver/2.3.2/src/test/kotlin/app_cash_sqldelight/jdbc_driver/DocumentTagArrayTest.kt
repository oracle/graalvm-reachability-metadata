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
import app.cash.sqldelight.driver.jdbc.JdbcPreparedStatement
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import java.sql.Types
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises JDBC SQL-array storage and membership queries for a tagged-document directory.
 */
@Timeout(60)
class DocumentTagArrayTest {
    @Test
    fun searchesTagsAndDistinguishesUnclassifiedDocumentsFromExplicitlyUntaggedOnes() {
        val dataSource: JdbcDataSource = JdbcDataSource().apply {
            setURL("jdbc:h2:mem:document_tag_arrays")
        }
        dataSource.connection.use {
            dataSource.asJdbcDriver().use { driver: JdbcDriver ->
                driver.execute(
                    null,
                    """
                    CREATE TABLE documents (
                        id BIGINT PRIMARY KEY,
                        title VARCHAR(200) NOT NULL,
                        tags VARCHAR(80) ARRAY
                    )
                    """.trimIndent(),
                    0
                )
                val directory: DocumentDirectory = DocumentDirectory(driver)
                val handbook: Document = Document(1L, "Operations handbook", listOf("ops", "on-call"))
                val checklist: Document = Document(2L, "Release checklist", listOf("ops", "release"))
                val unclassified: Document = Document(3L, "Imported draft", null)
                val untagged: Document = Document(4L, "General notice", emptyList())
                listOf(handbook, checklist, unclassified, untagged).forEach { document ->
                    directory.save(document)
                }

                assertEquals(listOf(handbook, checklist, unclassified, untagged), directory.all())
                assertEquals(listOf(handbook, checklist), directory.withTag("ops"))
                assertEquals(listOf(checklist), directory.withTag("release"))
                assertEquals(emptyList<Document>(), directory.withTag("unknown"))

                // Reclassification replaces the array, rather than retaining stale search tags.
                val revisedHandbook: Document = handbook.copy(tags = listOf("support", "customer's guide"))
                val classifiedDraft: Document = unclassified.copy(tags = listOf("release"))
                val clearedChecklist: Document = checklist.copy(tags = emptyList())
                directory.save(revisedHandbook)
                directory.save(classifiedDraft)
                directory.save(clearedChecklist)

                assertEquals(emptyList<Document>(), directory.withTag("ops"))
                assertEquals(listOf(revisedHandbook), directory.withTag("customer's guide"))
                assertEquals(listOf(classifiedDraft), directory.withTag("release"))
                assertEquals(
                    listOf(revisedHandbook, clearedChecklist, classifiedDraft, untagged),
                    directory.all()
                )
            }
        }
    }

    private class DocumentDirectory(private val driver: JdbcDriver) {
        fun save(document: Document) {
            val changed: Long = driver.execute(
                null,
                "MERGE INTO documents (id, title, tags) KEY (id) VALUES (?, ?, ?)",
                3
            ) {
                bindLong(0, document.id)
                bindString(1, document.title)
                (this as JdbcPreparedStatement).bindObject(2, document.tags?.toTypedArray(), Types.ARRAY)
            }.value
            check(changed == 1L)
        }

        fun all(): List<Document> = driver.executeQuery(
            null,
            "SELECT id, title, tags FROM documents ORDER BY id",
            mapper = { cursor -> QueryResult.Value(readDocuments(cursor as JdbcCursor)) },
            parameters = 0
        ).value

        fun withTag(tag: String): List<Document> = driver.executeQuery(
            null,
            "SELECT id, title, tags FROM documents WHERE ARRAY_CONTAINS(tags, ?) ORDER BY id",
            mapper = { cursor -> QueryResult.Value(readDocuments(cursor as JdbcCursor)) },
            parameters = 1,
            binders = { bindString(0, tag) }
        ).value

        private fun readDocuments(cursor: JdbcCursor): List<Document> {
            val documents: MutableList<Document> = mutableListOf()
            while (cursor.next().value) {
                // JDBC arrays expose Object[]; convert elements before leaving the cursor's scope.
                val tags: List<String>? = cursor.getArray<Any>(2)?.map { tag -> tag as String }
                documents.add(
                    Document(
                        id = requireNotNull(cursor.getLong(0)),
                        title = requireNotNull(cursor.getString(1)),
                        tags = tags
                    )
                )
            }
            return documents.toList()
        }
    }

    private data class Document(val id: Long, val title: String, val tags: List<String>?)
}
