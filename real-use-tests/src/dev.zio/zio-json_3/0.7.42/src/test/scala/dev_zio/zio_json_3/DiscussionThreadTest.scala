/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_json_3

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import zio.json.*

/** Exercises recursive codec derivation for loading and updating threaded discussions. */
class DiscussionThreadTest {
  final case class Comment(id: String, body: String, replies: List[Comment])

  given JsonCodec[Comment] = DeriveJsonCodec.gen[Comment]

  private def appendReply(comment: Comment, parentId: String, reply: Comment): Comment = {
    if (comment.id == parentId) comment.copy(replies = comment.replies :+ reply)
    else comment.copy(replies = comment.replies.map(child => appendReply(child, parentId, reply)))
  }

  private def displayLines(comment: Comment, depth: Int = 0): List[String] = {
    List(("  " * depth) + comment.body) ++ comment.replies.flatMap(child => displayLines(child, depth + 1))
  }

  @Test
  @Timeout(30)
  def loadDiscussionAppendNestedReplyAndPublishTheUpdatedThread(): Unit = {
    val incoming: String = """
      {
        "id": "topic-1",
        "body": "How do I reset the router?",
        "replies": [
          {
            "id": "reply-1",
            "body": "Use the reset button.",
            "replies": [
              {"id":"reply-2","body":"For how long?","replies":[]}
            ]
          },
          {"id":"reply-3","body":"Save your settings first.","replies":[]}
        ]
      }
      """
    val expected: Comment = Comment(
      "topic-1",
      "How do I reset the router?",
      List(
        Comment("reply-1", "Use the reset button.", List(Comment("reply-2", "For how long?", Nil))),
        Comment("reply-3", "Save your settings first.", Nil)
      )
    )
    val decoded: Either[String, Comment] = incoming.fromJson[Comment]
    assertEquals(Right(expected), decoded)

    val thread: Comment = decoded.fold(error => fail[Comment](error), identity)
    val reply: Comment = Comment("reply-4", "Hold it for ten seconds.", Nil)
    val updated: Comment = appendReply(thread, "reply-2", reply)
    val published: String = updated.toJson
    val restored: Either[String, Comment] = published.fromJson[Comment]
    assertEquals(Right(updated), restored)

    val displayed: List[String] = restored.map(comment => displayLines(comment))
      .fold(error => fail[List[String]](error), identity)
    assertEquals(
      List(
        "How do I reset the router?",
        "  Use the reset button.",
        "    For how long?",
        "      Hold it for ten seconds.",
        "  Save your settings first."
      ),
      displayed
    )
    assertEquals(List(reply), updated.replies.head.replies.head.replies)
    assertEquals(expected.replies(1), updated.replies(1))

    val malformed: String = incoming.replace("\"For how long?\"", "42")
    val rejected: Either[String, Comment] = malformed.fromJson[Comment]
    assertTrue(rejected.isLeft, "Malformed nested replies must not be silently discarded")
    val error: String = rejected.swap.toOption.get
    assertTrue(error.contains("replies"), error)
    assertTrue(error.contains("body"), error)
  }
}
