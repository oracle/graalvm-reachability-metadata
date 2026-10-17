/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_exposed.exposed_jdbc

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.vendors.ColumnMetadata
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.exists
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.vendors.currentDialectMetadata
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Schema evolution through JDBC metadata introspection on MariaDB: a blog service deployed with a
 * release-1 schema upgrades in place to release 2, diffing its table definitions against the live
 * catalog to add a missing table, columns, a foreign key and an index, then verifies the result.
 */
class BlogSchemaUpgradeIntrospectionTest {

    /** The `posts` table as release 1 of the service defines it. */
    object PostsV1 : Table("posts") {
        val id = integer("id").autoIncrement()
        val title = varchar("title", 120)

        override val primaryKey = PrimaryKey(id)
    }

    /** The `authors` table, introduced in release 2. */
    object Authors : Table("authors") {
        val id = integer("id").autoIncrement()
        val handle = varchar("handle", 32).uniqueIndex("uq_authors_handle")

        override val primaryKey = PrimaryKey(id)
    }

    /** The `posts` table as release 2 defines it: an optional author, a flag, and a title index. */
    object PostsV2 : Table("posts") {
        val id = integer("id").autoIncrement()
        val title = varchar("title", 120)
        val authorId = optReference(
            "author_id",
            Authors.id,
            onDelete = ReferenceOption.SET_NULL,
            fkName = "fk_posts_author",
        )
        val published = bool("published").default(false)

        override val primaryKey = PrimaryKey(id)

        init {
            index("idx_posts_title", false, title)
        }
    }

    data class PostRow(val title: String, val author: String?, val published: Boolean)

    companion object {
        private const val PASSWORD = "blog-secret"
        private val containerName: String = "real-use-exposed-jdbc-blog-" + UUID.randomUUID()
        private var port: Int = 0

        private val jdbcUrl: String
            get() = "jdbc:mariadb://127.0.0.1:$port/blog"

        @BeforeAll
        @JvmStatic
        fun startMariaDb() {
            port = ServerSocket(0).use { it.localPort }
            docker(
                "run", "--rm", "-d", "--name", containerName,
                "-e", "MARIADB_ROOT_PASSWORD=$PASSWORD",
                "-e", "MARIADB_DATABASE=blog",
                "-p", "127.0.0.1:$port:3306",
                "mariadb:13.0",
            )
            awaitConnection(TimeUnit.SECONDS.toMillis(180))
        }

        @AfterAll
        @JvmStatic
        fun stopMariaDb() {
            docker("rm", "-f", containerName)
        }

        private fun docker(vararg args: String) {
            val process = ProcessBuilder(listOf("docker") + args).inheritIO().start()
            check(process.waitFor(5, TimeUnit.MINUTES)) { "docker ${args.first()} timed out" }
            check(process.exitValue() == 0) { "docker ${args.first()} failed with ${process.exitValue()}" }
        }

        /** Polls with a bounded deadline until the server accepts connections to the `blog` database. */
        private fun awaitConnection(timeoutMillis: Long) {
            val deadline = System.currentTimeMillis() + timeoutMillis
            var lastFailure: Exception? = null
            while (System.currentTimeMillis() < deadline) {
                try {
                    DriverManager.getConnection(jdbcUrl, "root", PASSWORD).use { connection ->
                        connection.createStatement().use { it.execute("SELECT 1") }
                    }
                    return
                } catch (e: Exception) {
                    lastFailure = e
                    Thread.sleep(250)
                }
            }
            throw IllegalStateException("MariaDB did not become reachable", lastFailure)
        }
    }

    @Test
    fun upgradesReleaseOneSchemaInPlaceByDiffingTheLiveCatalog() {
        val database = Database.connect(
            url = jdbcUrl,
            driver = "org.mariadb.jdbc.Driver",
            user = "root",
            password = PASSWORD,
        )

        transaction(database) {
            SchemaUtils.create(PostsV1)
            PostsV1.insert { it[title] = "Hello from release 1" }
        }

        val beforeUpgrade: Pair<Boolean, Set<String>> = transaction(database) {
            Authors.exists() to columnNames(currentDialectMetadata.tableColumns(PostsV2).getValue(PostsV2))
        }
        assertThat(beforeUpgrade.first).isFalse()
        assertThat(beforeUpgrade.second).containsExactlyInAnyOrder("id", "title")

        val migration: List<String> = transaction(database) {
            val statements = mutableListOf<String>()
            if (!Authors.exists()) {
                statements += SchemaUtils.createStatements(Authors)
            }
            statements.forEach { exec(it) }
            currentDialectMetadata.resetCaches()

            val columnStatements = SchemaUtils.addMissingColumnsStatements(PostsV2)
            columnStatements.forEach { exec(it) }
            statements += columnStatements

            val existingIndexNames = currentDialectMetadata.existingIndices(PostsV2)
                .getValue(PostsV2)
                .map { it.indexName }
                .toSet()
            PostsV2.indices
                .filterNot { it.indexName in existingIndexNames }
                .flatMap { SchemaUtils.createIndex(it) }
                .forEach {
                    exec(it)
                    statements += it
                }
            statements
        }
        assertThat(migration.joinToString("\n"))
            .containsIgnoringCase("CREATE TABLE IF NOT EXISTS authors")
            .containsIgnoringCase("ADD author_id")
            .containsIgnoringCase("ADD published")
            .containsIgnoringCase("fk_posts_author")
            .containsIgnoringCase("idx_posts_title")

        transaction(database) {
            currentDialectMetadata.resetCaches()
            assertThat(SchemaUtils.listTables().map { it.substringAfterLast('.').lowercase() })
                .contains("authors", "posts")

            val postColumns: Map<String, ColumnMetadata> = currentDialectMetadata.tableColumns(PostsV2)
                .getValue(PostsV2)
                .associateBy { it.name.lowercase() }
            assertThat(postColumns.keys).containsExactlyInAnyOrder("id", "title", "author_id", "published")
            assertThat(postColumns.getValue("author_id").nullable).isTrue()
            assertThat(postColumns.getValue("published").nullable).isFalse()
            assertThat(postColumns.getValue("id").autoIncrement).isTrue()
            assertThat(postColumns.getValue("title").size).isEqualTo(120)

            val primaryKey = currentDialectMetadata.existingPrimaryKeys(PostsV2).getValue(PostsV2)
            assertThat(primaryKey?.columnNames?.map { it.lowercase() }).containsExactly("id")

            val foreignKeys = currentDialectMetadata.columnConstraints(PostsV2).values.flatten()
            assertThat(foreignKeys.map { it.fkName.lowercase() }).containsExactly("fk_posts_author")
            assertThat(foreignKeys.single().targetTableName.lowercase()).isEqualTo("authors")
            assertThat(foreignKeys.single().deleteRule).isEqualTo(ReferenceOption.SET_NULL)

            val indexNames = currentDialectMetadata.existingIndices(PostsV2, Authors)
                .mapValues { (_, indices) -> indices.map { it.indexName.lowercase() } }
            assertThat(indexNames.getValue(PostsV2)).contains("idx_posts_title")
            assertThat(indexNames.getValue(Authors)).contains("uq_authors_handle")

            assertThat(SchemaUtils.addMissingColumnsStatements(PostsV2, Authors)).isEmpty()
        }

        val posts: List<PostRow> = transaction(database) {
            val authorId = Authors.insert { it[handle] = "ada" }[Authors.id]
            PostsV2.insert {
                it[title] = "Release 2 is live"
                it[PostsV2.authorId] = authorId
                it[published] = true
            }
            (PostsV2 leftJoin Authors)
                .selectAll()
                .orderBy(PostsV2.id)
                .map { PostRow(it[PostsV2.title], it[Authors.handle], it[PostsV2.published]) }
        }
        assertThat(posts).containsExactly(
            PostRow("Hello from release 1", null, false),
            PostRow("Release 2 is live", "ada", true),
        )

        val orphaned: List<PostRow> = transaction(database) {
            exec("DELETE FROM authors WHERE handle = 'ada'")
            PostsV2.selectAll()
                .where { PostsV2.published eq true }
                .map { PostRow(it[PostsV2.title], it[PostsV2.authorId]?.toString(), it[PostsV2.published]) }
        }
        assertThat(orphaned).containsExactly(PostRow("Release 2 is live", null, true))

        transaction(database) { SchemaUtils.drop(PostsV2, Authors) }
    }

    private fun columnNames(columns: List<ColumnMetadata>): Set<String> = columns.map { it.name.lowercase() }.toSet()
}
