/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_moshi.moshi_kotlin

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises recursive object graphs and concurrent adapter resolution in
 * `KotlinJsonAdapterFactory`: self-referential and mutually recursive Kotlin classes, deep trees,
 * cycle detection while encoding, nesting limits while decoding, and one `Moshi` instance shared
 * by many threads that resolve those recursive adapters at the same time.
 *
 * A document vault syncs its folder tree, where documents can carry attachment folders and
 * point at the document they were copied from, with several worker threads.
 */
@OptIn(ExperimentalStdlibApi::class)
public class DocumentVaultRecursiveGraphConcurrencyTest {

    /** A folder holds documents and subfolders of its own type. */
    public data class Folder(
        val name: String,
        val documents: List<Document> = emptyList(),
        val subfolders: List<Folder> = emptyList(),
    )

    /** A document refers back to folders through its attachments and to itself through its origin. */
    public data class Document(
        val title: String,
        val attachments: List<Folder> = emptyList(),
        val copiedFrom: Document? = null,
    )

    /** A share link that the sync client can chain into a ring by mistake. */
    public class ShareLink(public val token: String) {
        public var next: ShareLink? = null
    }

    /** One level of a version history kept as a linked list. */
    public data class Revision(val number: Int, val previous: Revision? = null)

    private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    private val sampleTree: Folder = Folder(
        name = "root",
        documents = listOf(
            Document(
                title = "contract.pdf",
                attachments = listOf(
                    Folder(
                        name = "scans",
                        documents = listOf(Document(title = "page-1.png")),
                    ),
                ),
                copiedFrom = Document(
                    title = "contract-draft.pdf",
                    copiedFrom = Document(title = "template.pdf"),
                ),
            ),
        ),
        subfolders = listOf(
            Folder(name = "archive", subfolders = listOf(Folder(name = "2025"))),
            Folder(name = "inbox"),
        ),
    )

    @Test
    fun mutuallyRecursiveTreeDecodesWithDefaultsAndRoundTrips() {
        val payload: String = """
            {
              "name": "root",
              "documents": [
                {
                  "title": "contract.pdf",
                  "attachments": [
                    {"name": "scans", "documents": [{"title": "page-1.png"}]}
                  ],
                  "copiedFrom": {
                    "title": "contract-draft.pdf",
                    "copiedFrom": {"title": "template.pdf"}
                  }
                }
              ],
              "subfolders": [
                {"name": "archive", "subfolders": [{"name": "2025"}]},
                {"name": "inbox"}
              ]
            }
        """.trimIndent()
        val adapter: JsonAdapter<Folder> = moshi.adapter<Folder>()

        val decoded: Folder = adapter.fromJson(payload)!!

        assertEquals(sampleTree, decoded)
        val contract: Document = decoded.documents.single()
        assertEquals("page-1.png", contract.attachments.single().documents.single().title)
        assertEquals("template.pdf", contract.copiedFrom?.copiedFrom?.title)
        assertNull(contract.copiedFrom?.copiedFrom?.copiedFrom)
        assertEquals(emptyList<Folder>(), decoded.subfolders[1].subfolders)
        assertEquals(sampleTree, adapter.fromJson(adapter.toJson(sampleTree)))
    }

    @Test
    fun nullOriginIsOmittedWhileEmptyCollectionsAreKept() {
        val adapter: JsonAdapter<Document> = moshi.adapter<Document>()

        val json: String = adapter.toJson(Document(title = "notes.txt"))

        assertEquals("""{"title":"notes.txt","attachments":[]}""", json)
        assertEquals(
            """{"title":"notes.txt","attachments":[],"copiedFrom":null}""",
            adapter.serializeNulls().toJson(Document(title = "notes.txt")),
        )
    }

    @Test
    fun nestedErrorsReportTheirPathThroughTheRecursion() {
        val adapter: JsonAdapter<Folder> = moshi.adapter<Folder>()
        val payload: String = """
            {"name":"root","subfolders":[{"name":"a"},{"name":"b","documents":[{"title":"x"},{"attachments":[]}]}]}
        """.trimIndent()

        val error: JsonDataException = assertThrows(JsonDataException::class.java) {
            adapter.fromJson(payload)
        }

        // The missing property is detected after the object closes, so the index has advanced.
        assertEquals("Required value 'title' missing at \$.subfolders[1].documents[2]", error.message)
    }

    @Test
    fun longRevisionChainsRoundTripBelowTheNestingLimit() {
        val adapter: JsonAdapter<Revision> = moshi.adapter<Revision>()
        var head: Revision = Revision(number = 1)
        for (number in 2..200) {
            head = Revision(number = number, previous = head)
        }

        val decoded: Revision = adapter.fromJson(adapter.toJson(head))!!

        assertEquals(head, decoded)
        var depth: Int = 0
        var cursor: Revision? = decoded
        while (cursor != null) {
            depth++
            cursor = cursor.previous
        }
        assertEquals(200, depth)
    }

    @Test
    fun hostileDeeplyNestedInputIsRejected() {
        val adapter: JsonAdapter<Revision> = moshi.adapter<Revision>()
        val levels: Int = 300
        val payload: String = buildString {
            repeat(levels) { append("{\"number\":").append(it).append(",\"previous\":") }
            append("null")
            repeat(levels) { append('}') }
        }

        val error: JsonDataException = assertThrows(JsonDataException::class.java) {
            adapter.fromJson(payload)
        }

        assertTrue(error.message!!.startsWith("Nesting too deep"), "unexpected message: ${error.message}")
    }

    @Test
    fun cyclicShareLinksAreDetectedInsteadOfOverflowingTheStack() {
        val adapter: JsonAdapter<ShareLink> = moshi.adapter<ShareLink>()
        val first: ShareLink = ShareLink("alpha")
        val second: ShareLink = ShareLink("beta")
        first.next = second

        assertEquals("""{"token":"alpha","next":{"token":"beta"}}""", adapter.toJson(first))

        second.next = first
        val error: JsonDataException = assertThrows(JsonDataException::class.java) {
            adapter.toJson(first)
        }

        assertTrue(error.message!!.startsWith("Nesting too deep"), "unexpected message: ${error.message}")
        assertTrue(error.message!!.endsWith("circular reference?"), "unexpected message: ${error.message}")
    }

    @Test
    fun workersSharingOneMoshiResolveRecursiveAdaptersConcurrently() {
        val shared: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val expectedJson: String = moshi.adapter<Folder>().toJson(sampleTree)
        val workers: Int = 8
        val start: CountDownLatch = CountDownLatch(1)
        val pool: ExecutorService = Executors.newFixedThreadPool(workers)
        try {
            val results: List<Future<Pair<Folder, String>>> = (0 until workers).map { worker: Int ->
                pool.submit(
                    Callable {
                        assertTrue(start.await(30, TimeUnit.SECONDS))
                        // Half the workers enter the recursion through Document, half through Folder.
                        if (worker % 2 == 0) {
                            shared.adapter<Document>().toJson(sampleTree.documents.single())
                        }
                        val adapter: JsonAdapter<Folder> = shared.adapter<Folder>()
                        val decoded: Folder = adapter.fromJson(expectedJson)!!
                        decoded to adapter.toJson(decoded)
                    },
                )
            }
            start.countDown()

            for (result: Future<Pair<Folder, String>> in results) {
                val (decoded: Folder, encoded: String) = result.get(60, TimeUnit.SECONDS)
                assertEquals(sampleTree, decoded)
                assertEquals(expectedJson, encoded)
            }
        } finally {
            pool.shutdownNow()
        }

        val cached: JsonAdapter<Folder> = shared.adapter(Folder::class.java)
        assertSame(cached, shared.adapter(Folder::class.java))
    }
}
