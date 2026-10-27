/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_exposed.exposed_core

import java.sql.SQLException
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.Index
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Sequence
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.exists
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test

/**
 * Schema definition and evolution: a library catalog declares unique indexes, check
 * constraints, cascading foreign keys and a sequence-backed column, lets the database enforce
 * them, then migrates the `books` table by adding the columns and indexes a new release declares.
 */
class LibrarySchemaEvolutionTest {

    object Authors : Table("authors") {
        val id: Column<Int> = integer("id").autoIncrement()
        val email: Column<String> = varchar("email", 64).uniqueIndex("authors_email_uq")
        val name: Column<String> = varchar("name", 64)

        override val primaryKey = PrimaryKey(id)
    }

    /** The `books` table as the first release of the application declares it. */
    object Books : Table("books") {
        val id: Column<Int> = integer("id").autoIncrement()
        val author: Column<Int> = integer("author_id").references(Authors.id, onDelete = ReferenceOption.CASCADE)
        val title: Column<String> = varchar("title", 128)
        val pages: Column<Int> = integer("pages").check("books_pages_positive") { it greater 0 }

        override val primaryKey = PrimaryKey(id)

        init {
            uniqueIndex("books_author_title_uq", author, title)
        }
    }

    /** The same `books` table after a release adds an optional subtitle and a title index. */
    object BooksV2 : Table("books") {
        val id: Column<Int> = integer("id").autoIncrement()
        val author: Column<Int> = integer("author_id").references(Authors.id, onDelete = ReferenceOption.CASCADE)
        val title: Column<String> = varchar("title", 128)
        val pages: Column<Int> = integer("pages").check("books_pages_positive") { it greater 0 }
        val subtitle: Column<String?> = varchar("subtitle", 128).nullable()

        override val primaryKey = PrimaryKey(id)

        init {
            uniqueIndex("books_author_title_uq", author, title)
            index("books_title_idx", false, title)
        }
    }

    object Invoices : Table("invoices") {
        val numbers: Sequence = Sequence("invoice_number_seq", startWith = 1000, incrementBy = 10)
        val number: Column<Long> = long("number").autoIncrement(numbers)
        val author: Column<Int> = integer("author_id").references(Authors.id)

        override val primaryKey = PrimaryKey(number)
    }

    private fun sqlStateOf(error: Throwable): String? = (error.cause as SQLException).sqlState

    private fun addAuthor(email: String, name: String): Int = Authors.insert {
        it[Authors.email] = email
        it[Authors.name] = name
    } get Authors.id

    @Test
    fun constraintsAreEnforcedAndSchemaMigratesToTheNewRelease() {
        val database: Database = Database.connect(
            url = "jdbc:h2:mem:library_schema;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
            databaseConfig = DatabaseConfig { defaultMaxAttempts = 1 },
        )

        transaction(database) {
            assertThat(SchemaUtils.createStatements(Authors)).containsExactly(
                "CREATE TABLE IF NOT EXISTS AUTHORS (ID INT AUTO_INCREMENT PRIMARY KEY, " +
                    "EMAIL VARCHAR(64) NOT NULL, \"name\" VARCHAR(64) NOT NULL)",
                "ALTER TABLE AUTHORS ADD CONSTRAINT authors_email_uq UNIQUE (EMAIL)",
            )
            assertThat(Books.foreignKeys.single().deleteRule).isEqualTo(ReferenceOption.CASCADE)
            assertThat(Books.checkConstraints().map { it.checkName }).containsExactly("books_pages_positive")
        }

        val (orwell: Int, huxley: Int) = transaction(database) {
            SchemaUtils.create(Authors, Books, Invoices)
            assertThat(Books.exists()).isTrue()
            assertThat(Invoices.numbers.exists()).isTrue()
            assertThat(SchemaUtils.listTables().map { it.substringAfter('.').lowercase() })
                .contains("authors", "books", "invoices")

            val orwell: Int = addAuthor("orwell@example.org", "George Orwell")
            val huxley: Int = addAuthor("huxley@example.org", "Aldous Huxley")
            listOf(orwell to "Animal Farm" to 112, orwell to "Nineteen Eighty-Four" to 328, huxley to "Island" to 354)
                .forEach { (book: Pair<Int, String>, pageCount: Int) ->
                    Books.insert {
                        it[author] = book.first
                        it[title] = book.second
                        it[pages] = pageCount
                    }
                }
            orwell to huxley
        }

        val duplicateEmail: Throwable = catchFailure(database) { addAuthor("orwell@example.org", "Eric Blair") }
        assertThat(sqlStateOf(duplicateEmail)).isEqualTo("23505")

        val duplicateTitle: Throwable = catchFailure(database) {
            Books.insert {
                it[author] = orwell
                it[title] = "Animal Farm"
                it[pages] = 95
            }
        }
        assertThat(sqlStateOf(duplicateTitle)).isEqualTo("23505")

        val emptyBook: Throwable = catchFailure(database) {
            Books.insert {
                it[author] = huxley
                it[title] = "Untitled"
                it[pages] = 0
            }
        }
        assertThat(sqlStateOf(emptyBook)).isEqualTo("23513")

        val unknownAuthor: Throwable = catchFailure(database) {
            Books.insert {
                it[author] = 9_999
                it[title] = "Ghost Book"
                it[pages] = 10
            }
        }
        assertThat(sqlStateOf(unknownAuthor)).isEqualTo("23506")

        val invoiceNumbersIssued: List<Long> = transaction(database) {
            listOf(orwell, huxley, orwell).map { authorId: Int ->
                Invoices.insert { it[Invoices.author] = authorId } get Invoices.number
            }
        }
        assertThat(invoiceNumbersIssued).containsExactly(1000L, 1010L, 1020L)

        val titleIndex: Index = BooksV2.indices.single { it.indexName == "books_title_idx" }
        val migration: List<String> = transaction(database) {
            SchemaUtils.addMissingColumnsStatements(BooksV2) + SchemaUtils.createIndex(titleIndex)
        }
        assertThat(migration).containsExactly(
            "ALTER TABLE BOOKS ADD SUBTITLE VARCHAR(128) NULL",
            "CREATE INDEX books_title_idx ON BOOKS (TITLE)",
        )

        val migratedTitles: Map<String, String?> = transaction(database) {
            migration.forEach { exec(it) }
            BooksV2.insert {
                it[author] = huxley
                it[title] = "Brave New World"
                it[pages] = 311
                it[subtitle] = "A Novel"
            }
            BooksV2.selectAll()
                .orderBy(BooksV2.title to SortOrder.ASC)
                .associate { it[BooksV2.title] to it[BooksV2.subtitle] }
        }
        assertThat(migratedTitles).containsExactlyEntriesOf(
            linkedMapOf(
                "Animal Farm" to null,
                "Brave New World" to "A Novel",
                "Island" to null,
                "Nineteen Eighty-Four" to null,
            ),
        )
        assertThat(transaction(database) { SchemaUtils.addMissingColumnsStatements(BooksV2) }).isEmpty()

        val remainingTitles: List<String> = transaction(database) {
            Invoices.deleteWhere { Invoices.author eq orwell }
            Authors.deleteWhere { Authors.id eq orwell }
            BooksV2.selectAll().orderBy(BooksV2.title to SortOrder.ASC).map { it[BooksV2.title] }
        }
        assertThat(remainingTitles).containsExactly("Brave New World", "Island")

        transaction(database) {
            SchemaUtils.drop(Invoices, BooksV2, Authors)
            assertThat(BooksV2.exists()).isFalse()
            assertThat(Invoices.numbers.exists()).isFalse()
        }
    }

    /** Runs [block] in its own transaction and returns the failure the database reports. */
    private fun catchFailure(database: Database, block: () -> Unit): Throwable {
        val failure: Throwable? = runCatching { transaction(database) { block() } }.exceptionOrNull()
        assertThat(failure).isInstanceOf(ExposedSQLException::class.java)
        return failure!!
    }
}
