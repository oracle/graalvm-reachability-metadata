/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_exposed.exposed_core

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.dao.EntityChange
import org.jetbrains.exposed.v1.dao.EntityChangeType
import org.jetbrains.exposed.v1.dao.EntityHook
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.dao.with
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test

/**
 * DAO entity layer over id tables: a bookshelf application maps authors, books and tags to
 * `IntIdTable`s, navigates `referencedOn`/`referrersOn`/`via` relations, eager-loads them, and
 * observes entity changes through `EntityHook`.
 */
class BookshelfEntityDaoTest {

    object Authors : IntIdTable("authors") {
        val name = varchar("name", 64)
    }

    object Books : IntIdTable("books") {
        val title = varchar("title", 128)
        val year = integer("year")
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
        var year by Books.year
        var author by Author referencedOn Books.author
        var tags by Tag via BookTags
    }

    class Tag(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<Tag>(Tags)

        var label by Tags.label
    }

    @Test
    fun managesBookshelfThroughEntities() {
        val database: Database = Database.connect(
            url = "jdbc:h2:mem:bookshelf_dao;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        transaction(database) {
            SchemaUtils.create(Authors, Books, Tags, BookTags)
        }

        val changes: MutableList<EntityChange> = mutableListOf()
        val subscriber: (EntityChange) -> Unit = EntityHook.subscribe { change -> changes.add(change) }

        val leGuinId: EntityID<Int> = transaction(database) {
            val classic = Tag.new { label = "classic" }
            val scienceFiction = Tag.new { label = "science-fiction" }
            val fantasy = Tag.new { label = "fantasy" }

            val leGuin = Author.new { name = "Ursula K. Le Guin" }
            val herbert = Author.new { name = "Frank Herbert" }

            Book.new {
                title = "The Left Hand of Darkness"
                year = 1969
                author = leGuin
            }.tags = Tag.forIds(listOf(classic.id.value, scienceFiction.id.value))
            Book.new {
                title = "A Wizard of Earthsea"
                year = 1968
                author = leGuin
            }.tags = Tag.forIds(listOf(fantasy.id.value))
            Book.new {
                title = "Dune"
                year = 1965
                author = herbert
            }.tags = Tag.forIds(listOf(classic.id.value, scienceFiction.id.value))
            leGuin.id
        }

        val createdBooks: Int = changes.count { change ->
            change.changeType == EntityChangeType.Created && change.entityClass == Book
        }
        assertThat(createdBooks).isEqualTo(3)

        val shelf: List<String> = transaction(database) {
            Book.all()
                .orderBy(Books.year to SortOrder.ASC)
                .with(Book::author, Book::tags)
                .map { book -> "${book.year} ${book.title} by ${book.author.name} ${book.tags.map { it.label }.sorted()}" }
        }
        assertThat(shelf).containsExactly(
            "1965 Dune by Frank Herbert [classic, science-fiction]",
            "1968 A Wizard of Earthsea by Ursula K. Le Guin [fantasy]",
            "1969 The Left Hand of Darkness by Ursula K. Le Guin [classic, science-fiction]",
        )

        val leGuinTitles: List<String> = transaction(database) {
            val leGuin: Author = Author[leGuinId]
            leGuin.books.orderBy(Books.title to SortOrder.ASC).map { it.title }
        }
        assertThat(leGuinTitles).containsExactly("A Wizard of Earthsea", "The Left Hand of Darkness")

        changes.clear()
        transaction(database) {
            val dune: Book = Book.find { Books.title eq "Dune" }.single()
            dune.title = "Dune (Deluxe Edition)"
            dune.year = 2019
        }
        assertThat(changes.map { it.changeType to it.entityClass })
            .containsExactly(EntityChangeType.Updated to Book)

        val recent: List<String> = transaction(database) {
            Book.find { Books.year greaterEq 1969 }
                .orderBy(Books.year to SortOrder.DESC)
                .map { it.title }
        }
        assertThat(recent).containsExactly("Dune (Deluxe Edition)", "The Left Hand of Darkness")

        transaction(database) {
            Author[leGuinId].delete()
        }
        val remaining: Pair<List<String>, Long> = transaction(database) {
            Book.all().map { it.title } to BookTags.selectAll().count()
        }
        assertThat(remaining.first).containsExactly("Dune (Deluxe Edition)")
        assertThat(remaining.second).isEqualTo(2)
        assertThat(transaction(database) { Author.findById(leGuinId) }).isNull()

        EntityHook.unsubscribe(subscriber)
        transaction(database) {
            SchemaUtils.drop(BookTags, Tags, Books, Authors)
        }
    }
}
