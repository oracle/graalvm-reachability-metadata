/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework.play_json_3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import play.api.libs.functional.syntax.*
import play.api.libs.json.Format
import play.api.libs.json.JsError
import play.api.libs.json.JsNull
import play.api.libs.json.JsObject
import play.api.libs.json.JsPath
import play.api.libs.json.JsResult
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.JsonValidationError
import play.api.libs.json.OFormat
import play.api.libs.json.OWrites
import play.api.libs.json.Reads
import play.api.libs.json.Writes

/**
 * Recursive data structures encoded through lazy path formats (`lazyRead`, `lazyReadNullable`,
 * `lazyWrite`, `lazyFormatNullable`) and recursive macro-derived formats.
 *
 * A discussion forum serves comment threads as nested JSON: every comment carries its replies,
 * every post quotes the post it answers, and every board sits in a tree of sub-boards. The
 * codecs refer to themselves, so they are built lazily; threads of any depth round-trip, a
 * missing `replies` field reads as a leaf, and a broken comment deep in a thread is reported at
 * the full nested path that leads to it.
 */
class DiscussionThreadRecursiveFormatsTest {
  import DiscussionThreadRecursiveFormatsTest.*

  @Test
  def nestedCommentThreadsRoundTripAtAnyDepth(): Unit = {
    val thread: Comment = Comment(
      id = 1L,
      author = "ada",
      body = "Does the cache survive a restart?",
      replies = Seq(
        Comment(2L, "grace", "Only with the disk store enabled.", Seq(
          Comment(4L, "ada", "Where is that configured?", Seq(
            Comment(5L, "grace", "In storage.conf, key cache.disk.", Nil)
          ))
        )),
        Comment(3L, "linus", "Not by default.", Nil)
      )
    )

    val json: JsValue = Json.toJson(thread)

    assertEquals("grace", (json \ "replies" \ 0 \ "author").as[String])
    assertEquals(
      "In storage.conf, key cache.disk.",
      (json \ "replies" \ 0 \ "replies" \ 0 \ "replies" \ 0 \ "body").as[String]
    )
    assertEquals(Seq.empty[JsValue], (json \ "replies" \ 1 \ "replies").as[Seq[JsValue]])
    assertEquals(Seq(1L, 2L, 4L, 5L, 3L), (json \\ "id").map(_.as[Long]))

    val restored: Comment = Json.parse(Json.stringify(json)).as[Comment]
    assertEquals(thread, restored)
    assertEquals(5, restored.size)
    assertEquals(4, restored.depth)
    assertEquals(Set("ada", "grace", "linus"), restored.participants)
  }

  @Test
  def missingRepliesReadAsLeavesAndDeepErrorsCarryTheirPath(): Unit = {
    val compact: JsValue = Json.parse(
      """{"id": 10, "author": "ken", "body": "Release notes?",
        | "replies": [{"id": 11, "author": "dmr", "body": "Tomorrow."}]}""".stripMargin
    )
    val leafy: Comment = compact.as[Comment]
    assertEquals(Seq(Comment(11L, "dmr", "Tomorrow.", Nil)), leafy.replies)
    assertEquals(Json.arr(), (Json.toJson(leafy) \ "replies" \ 0 \ "replies").get)

    val broken: JsValue = Json.parse(
      """{"id": 20, "author": "ada", "body": "Benchmarks are in.",
        | "replies": [
        |   {"id": 21, "author": "grace", "body": "Nice.", "replies": []},
        |   {"id": 22, "author": "linus", "body": "Which JVM?", "replies": [
        |     {"id": 23, "body": "Twenty-five."},
        |     {"id": "24", "author": "ken", "body": "And flags?"}
        |   ]}
        | ]}""".stripMargin
    )

    val result: JsResult[Comment] = broken.validate[Comment]
    assertTrue(result.isError)
    val errorPaths: Set[JsPath] = result match {
      case JsError(errors) => errors.map(_._1).toSet
      case JsSuccess(_, _) => Set.empty
    }
    val second: JsPath = JsPath \ "replies" \ 1 \ "replies"
    assertEquals(Set(second \ 0 \ "author", second \ 1 \ "id"), errorPaths)

    val missingAuthor: Seq[JsonValidationError] = result match {
      case JsError(errors) => errors.collect { case (path, errs) if path == second \ 0 \ "author" => errs }.flatten.toSeq
      case JsSuccess(_, _) => Nil
    }
    assertEquals(Seq("error.path.missing"), missingAuthor.flatMap(_.messages))
  }

  @Test
  def quotedPostChainsUseLazyNullableFormats(): Unit = {
    val original: Post = Post(100L, "Shipping 3.0 today.", None)
    val reply: Post = Post(101L, "Congrats!", Some(original))
    val replyToReply: Post = Post(102L, "Seconded.", Some(reply))

    val json: JsObject = Json.toJsObject(replyToReply)

    assertEquals(100L, (json \ "inReplyTo" \ "inReplyTo" \ "id").as[Long])
    assertFalse((json \ "inReplyTo" \ "inReplyTo").as[JsObject].keys.contains("inReplyTo"))
    assertEquals(replyToReply, json.as[Post])
    assertEquals(Seq(102L, 101L, 100L), json.as[Post].chain.map(_.id))

    val explicitNull: JsValue = Json.obj("id" -> 200L, "text" -> "First!", "inReplyTo" -> JsNull)
    assertEquals(JsSuccess(Post(200L, "First!", None)), explicitNull.validate[Post])

    val badQuote: JsValue = Json.obj("id" -> 201L, "text" -> "Re:", "inReplyTo" -> Json.obj("id" -> 7L))
    val quoteErrors: Set[JsPath] = badQuote.validate[Post] match {
      case JsError(errors) => errors.map(_._1).toSet
      case JsSuccess(_, _) => Set.empty
    }
    assertEquals(Set(JsPath \ "inReplyTo" \ "text"), quoteErrors)
  }

  @Test
  def boardTreesUseRecursiveMacroFormats(): Unit = {
    val forum: Board = Board(
      "All boards",
      List(
        Board("Engineering", List(Board("Compilers", Nil), Board("Runtimes", List(Board("GC tuning", Nil))))),
        Board("Off-topic", Nil)
      )
    )

    val json: JsValue = Json.toJson(forum)
    assertEquals("GC tuning", (json \ "children" \ 0 \ "children" \ 1 \ "children" \ 0 \ "name").as[String])

    val restored: Board = json.validate[Board].get
    assertEquals(forum, restored)
    assertEquals(
      List("All boards", "Engineering", "Compilers", "Runtimes", "GC tuning", "Off-topic"),
      restored.flatten
    )

    val invalid: JsValue = Json.parse("""{"name": "Root", "children": [{"name": "A", "children": [{"children": []}]}]}""")
    val boardErrors: Set[JsPath] = invalid.validate[Board] match {
      case JsError(errors) => errors.map(_._1).toSet
      case JsSuccess(_, _) => Set.empty
    }
    assertEquals(Set(JsPath \ "children" \ 0 \ "children" \ 0 \ "name"), boardErrors)
  }
}

object DiscussionThreadRecursiveFormatsTest {

  final case class Comment(id: Long, author: String, body: String, replies: Seq[Comment]) {
    def size: Int = 1 + replies.map(_.size).sum

    def depth: Int = 1 + replies.map(_.depth).maxOption.getOrElse(0)

    def participants: Set[String] = replies.foldLeft(Set(author))(_ ++ _.participants)
  }

  object Comment {
    implicit lazy val commentReads: Reads[Comment] = (
      (JsPath \ "id").read[Long] and
        (JsPath \ "author").read[String] and
        (JsPath \ "body").read[String] and
        (JsPath \ "replies").lazyReadNullable(Reads.seq[Comment](using commentReads)).map(_.getOrElse(Seq.empty))
    )(Comment.apply)

    implicit lazy val commentWrites: OWrites[Comment] = (
      (JsPath \ "id").write[Long] and
        (JsPath \ "author").write[String] and
        (JsPath \ "body").write[String] and
        (JsPath \ "replies").lazyWrite(Writes.seq[Comment](using commentWrites))
    )(comment => (comment.id, comment.author, comment.body, comment.replies))
  }

  final case class Post(id: Long, text: String, inReplyTo: Option[Post]) {
    def chain: Seq[Post] = this +: inReplyTo.map(_.chain).getOrElse(Seq.empty)
  }

  object Post {
    implicit lazy val postFormat: OFormat[Post] = (
      (JsPath \ "id").format[Long] and
        (JsPath \ "text").format[String] and
        (JsPath \ "inReplyTo").lazyFormatNullable(postFormat: Format[Post])
    )(Post.apply, post => (post.id, post.text, post.inReplyTo))
  }

  final case class Board(name: String, children: List[Board]) {
    def flatten: List[String] = name :: children.flatMap(_.flatten)
  }

  object Board {
    implicit lazy val boardFormat: OFormat[Board] = Json.format[Board]
  }
}
