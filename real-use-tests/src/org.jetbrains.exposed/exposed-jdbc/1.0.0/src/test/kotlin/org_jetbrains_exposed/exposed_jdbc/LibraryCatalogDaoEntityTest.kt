/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_exposed.exposed_jdbc

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.SqlLogger
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.statements.StatementContext
import org.jetbrains.exposed.v1.dao.EntityChange
import org.jetbrains.exposed.v1.dao.EntityChangeType
import org.jetbrains.exposed.v1.dao.EntityHook
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.dao.exceptions.EntityNotFoundException
import org.jetbrains.exposed.v1.dao.with
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.SizedCollection
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test

/**
 * DAO entity mapping over JDBC: a library catalog stores authors, books and tags as `IntEntity`
 * classes with one-to-many and many-to-many relations, eager loading, the per-transaction entity
 * cache and change notifications delivered on commit.
 */
class LibraryCatalogDaoEntityTest {

    object Authors : IntIdTable("authors") {
        val name = varchar("name", 64)
    }

    object Books : IntIdTable("books") {
        val title = varchar("title", 128)
        val published = integer("published")
        val author = reference("author_id", Authors, onDelete = ReferenceOption.CASCADE)
    }

    object Tags : IntIdTable("tags") {
        val label = varchar("label", 32).uniqueIndex()
    }

    object BookTags : Table("book_tags") {
        val book = reference("book_id", Books, onDelete = ReferenceOption.CASCADE)
        val tag = reference("tag_id", Tags, onDelete = ReferenceOption.CASCADE)

        override val primaryKey = PrimaryKey(book, tag)
    }

    class Author(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<Author>(Authors)

        var name by Authors.name
        val books by Book referrersOn Books.author
    }

    class Book(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<Book>(Books)

        var title by Books.title
        var published by Books.published
        var author by Author referencedOn Books.author
        var tags by Tag via BookTags
    }

    class Tag(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<Tag>(Tags)

        var label by Tags.label
    }

    class SelectCounter : SqlLogger {
        var selects: Int = 0

        override fun log(context: StatementContext, transaction: Transaction) {
            if (context.sql(transaction).startsWith("SELECT")) {
                selects++
            }
        }
    }

    @Test
    fun catalogEntitiesPersistRelationsAndNotifyOnCommit() {
        val database = Database.connect("jdbc:h2:mem:catalog;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        val changes: MutableList<Pair<String, EntityChangeType>> = mutableListOf()
        val subscriber: (EntityChange) -> Unit = { change ->
            if (change.entityClass == Book) {
                changes += change.entityId.value.toString() to change.changeType
            }
        }
        var duneId = 0
        EntityHook.subscribe(subscriber)
        try {
            transaction(database) { SchemaUtils.create(Authors, Books, Tags, BookTags) }

            duneId = transaction(database) {
                val herbert = Author.new { name = "Frank Herbert" }
                val leGuin = Author.new { name = "Ursula K. Le Guin" }
                val classic = Tag.new { label = "classic" }
                val ecology = Tag.new { label = "ecology" }
                val anarchy = Tag.new { label = "anarchy" }

                val dune = Book.new {
                    title = "Dune"
                    published = 1965
                    author = herbert
                }
                dune.tags = SizedCollection(listOf(classic, ecology))
                Book.new {
                    title = "The Dispossessed"
                    published = 1974
                    author = leGuin
                }.tags = SizedCollection(listOf(classic, anarchy))
                Book.new {
                    title = "The Word for World Is Forest"
                    published = 1972
                    author = leGuin
                }.tags = SizedCollection(listOf(ecology))
                dune.id.value
            }

            transaction(database) {
                val dune: Book = Book[duneId]
                assertThat(Book.findById(duneId)).isSameAs(dune)
                assertThat(dune.author.name).isEqualTo("Frank Herbert")
                assertThat(dune.tags.map { it.label }).containsExactlyInAnyOrder("classic", "ecology")

                val leGuin: Author = Author.find { Authors.name eq "Ursula K. Le Guin" }.single()
                assertThat(leGuin.books.orderBy(Books.published to SortOrder.ASC).map { it.title })
                    .containsExactly("The Word for World Is Forest", "The Dispossessed")

                val seventies: List<String> = Book.find { Books.published greaterEq 1970 }
                    .orderBy(Books.title to SortOrder.ASC)
                    .map { it.title }
                assertThat(seventies).containsExactly("The Dispossessed", "The Word for World Is Forest")
                assertThat(Book.count()).isEqualTo(3L)
            }

            transaction(database) {
                val counter = SelectCounter()
                val books: List<Book> = Book.all().orderBy(Books.published to SortOrder.ASC)
                    .with(Book::author, Book::tags)
                    .toList()
                addLogger(counter)
                val shelf: List<String> = books.map { book ->
                    "${book.title} by ${book.author.name} [${book.tags.map { it.label }.sorted().joinToString()}]"
                }
                assertThat(shelf).containsExactly(
                    "Dune by Frank Herbert [classic, ecology]",
                    "The Word for World Is Forest by Ursula K. Le Guin [ecology]",
                    "The Dispossessed by Ursula K. Le Guin [anarchy, classic]",
                )
                assertThat(counter.selects).isZero()
            }

            transaction(database) {
                val dune: Book = Book[duneId]
                dune.published = 1966
                dune.title = "Dune (revised)"
            }
            transaction(database) {
                assertThat(Book[duneId].published).isEqualTo(1966)
                Book[duneId].delete()
            }

            transaction(database) {
                assertThatThrownBy { Book[duneId] }.isInstanceOf(EntityNotFoundException::class.java)
                assertThat(Tag.all().map { it.label }).containsExactlyInAnyOrder("classic", "ecology", "anarchy")
                assertThat(Author.all().map { it.books.count() }).containsExactlyInAnyOrder(0L, 2L)
                SchemaUtils.drop(BookTags, Tags, Books, Authors)
            }
        } finally {
            EntityHook.unsubscribe(subscriber)
        }

        val duneKey: String = duneId.toString()
        assertThat(changes.filter { it.first == duneKey }.map { it.second }).containsSubsequence(
            EntityChangeType.Created,
            EntityChangeType.Updated,
            EntityChangeType.Removed,
        )
        assertThat(changes.count { it.second == EntityChangeType.Created }).isEqualTo(3)
    }
}
