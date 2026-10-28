/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_lihaoyi.upickle_3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import upickle.default.*

/**
 * Derived codecs for Scala 3 enums, type-parameterized generic case classes and recursive data
 * types, as a document-management API pages through a folder tree and its comment threads.
 */
class DocumentTreeGenericEnumCodecTest {

  import DocumentTreeGenericEnumCodecTest.*

  @Test
  def recursiveFolderTreeRoundTripsAndKeepsItsShape(): Unit = {
    val tree: Node = Node.Dir(
      "projects",
      Permission.Admin,
      List(
        Node.File("readme.md", 1200L, Permission.Read),
        Node.Dir(
          "apollo",
          Permission.Write,
          List(Node.File("plan.pdf", 88_000L, Permission.Write), Node.Dir("archive", Permission.Read, Nil))
        )
      )
    )

    val json: String = write(tree)
    val restored: Node = read[Node](json)

    assertEquals(tree, restored)
    assertEquals(89_200L, restored.totalBytes)
    val parsed: ujson.Value = ujson.read(json)
    assertEquals("Dir", parsed("$type").str)
    assertEquals("Admin", parsed("permission").str)
    assertEquals("File", parsed("children")(0)("$type").str)
    assertEquals("archive", parsed("children")(1)("children")(1)("name").str)
  }

  @Test
  def simpleEnumCasesAreWrittenAsTheirNames(): Unit = {
    assertEquals("\"Write\"", write(Permission.Write))
    assertEquals(Permission.Read, read[Permission]("\"Read\""))
    assertEquals(
      Map(Permission.Read -> 3, Permission.Admin -> 1),
      read[Map[Permission, Int]](write(Map(Permission.Read -> 3, Permission.Admin -> 1)))
    )
    assertEquals(Permission.values.toList, read[List[Permission]]("""["Read","Write","Admin"]"""))
  }

  @Test
  def genericPageEnvelopeCarriesDifferentItemTypes(): Unit = {
    val files: Page[Node] = Page(
      items = List(Node.File("a.txt", 10L, Permission.Read), Node.File("b.txt", 20L, Permission.Write)),
      cursor = Some("b.txt"),
      total = 5
    )
    val names: Page[String] = Page(items = List("alpha", "beta"), cursor = None, total = 2)

    val filesJson: String = write(files)
    val namesJson: String = write(names)

    assertEquals(files, read[Page[Node]](filesJson))
    assertEquals(names, read[Page[String]](namesJson))
    assertEquals("""{"items":["alpha","beta"],"cursor":null,"total":2}""", namesJson)
    assertEquals("b.txt", ujson.read(filesJson)("cursor").str)
  }

  @Test
  def genericOutcomeEnumWrapsSuccessesAndFailures(): Unit = {
    val ok: Outcome[Page[String]] = Outcome.Ok(Page(List("draft.md"), None, 1))
    val failed: Outcome[Page[String]] = Outcome.Failed(404, "folder not found")

    val okJson: String = write(ok)
    val failedJson: String = write(failed)

    assertEquals(ok, read[Outcome[Page[String]]](okJson))
    assertEquals(failed, read[Outcome[Page[String]]](failedJson))
    assertEquals("Failed", ujson.read(failedJson)("$type").str)
    assertEquals("draft.md", ujson.read(okJson)("value")("items")(0).str)
  }

  @Test
  def deeplyNestedCommentThreadIsReadFromClientPayload(): Unit = {
    val payload: String =
      """{
        |  "author": "ada",
        |  "text": "Ship it?",
        |  "replies": [
        |    {"author": "alan", "text": "Needs tests", "replies": [
        |      {"author": "ada", "text": "Added", "replies": []}
        |    ]},
        |    {"author": "grace", "text": "LGTM"}
        |  ]
        |}""".stripMargin

    val thread: Comment = read[Comment](payload)

    assertEquals(4, thread.size)
    assertEquals(3, thread.depth)
    assertEquals(List("ada", "alan", "ada", "grace"), thread.authors)
    assertEquals(Nil, thread.replies(1).replies)

    var deep: Comment = Comment("bot", "leaf")
    for (level <- 1 to 200) deep = Comment("bot", s"level $level", List(deep))
    val deepJson: String = write(deep)
    val restored: Comment = read[Comment](deepJson)
    assertEquals(201, restored.depth)
    assertTrue(deepJson.endsWith("""{"author":"bot","text":"leaf"}""" + "]}" * 200), deepJson.takeRight(80))
  }
}

object DocumentTreeGenericEnumCodecTest {

  enum Permission derives ReadWriter {
    case Read, Write, Admin
  }

  enum Node derives ReadWriter {
    case File(name: String, bytes: Long, permission: Permission)
    case Dir(name: String, permission: Permission, children: List[Node])

    def totalBytes: Long = this match {
      case File(_, bytes, _) => bytes
      case Dir(_, _, children) => children.map(_.totalBytes).sum
    }
  }

  final case class Page[T](items: List[T], cursor: Option[String], total: Int) derives ReadWriter

  enum Outcome[+A] derives ReadWriter {
    case Ok(value: A)
    case Failed(code: Int, message: String)
  }

  final case class Comment(author: String, text: String, replies: List[Comment] = Nil) derives ReadWriter {
    def size: Int = 1 + replies.map(_.size).sum
    def depth: Int = 1 + replies.map(_.depth).maxOption.getOrElse(0)
    def authors: List[String] = author :: replies.flatMap(_.authors)
  }
}
