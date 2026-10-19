/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_scalaland.chimney_3

import io.scalaland.chimney.PartialTransformer
import io.scalaland.chimney.Transformer
import io.scalaland.chimney.dsl.*
import io.scalaland.chimney.partial
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Derivation for recursive data types through self-referencing `given` transformers.
 *
 * A discussion forum stores comment threads, moderation rule trees and imported staff
 * hierarchies, all of which nest values of their own type to arbitrary depth. Chimney cannot
 * derive such mappings automatically, so the forum declares each one once as a `given` built
 * with `Transformer.define`, `Transformer.derive` or `PartialTransformer.derive`; the derived
 * code summons that same `given` for every nested occurrence. The scenario renders a comment
 * tree with a computed reply count, persists a sealed rule tree with renamed subtypes, and
 * validates a staff import whose errors are reported at their full nested path.
 */
class DiscussionForumRecursiveTypesTransformerTest {

  final case class Comment(id: Long, author: String, body: String, replies: List[Comment])
  final case class CommentView(id: Long, author: String, text: String, replyCount: Int, replies: Vector[CommentView])

  sealed trait Rule
  final case class Keyword(word: String) extends Rule
  final case class AllOf(rules: List[Rule]) extends Rule
  final case class AnyOf(rules: List[Rule]) extends Rule
  final case class Not(rule: Rule) extends Rule

  sealed trait StoredRule
  final case class KeywordNode(word: String) extends StoredRule
  final case class AllNode(rules: Vector[StoredRule]) extends StoredRule
  final case class AnyNode(rules: Vector[StoredRule]) extends StoredRule
  final case class NotNode(rule: StoredRule) extends StoredRule

  final case class Category(slug: String, title: String, parent: Option[Category])
  final case class CategoryRow(slug: String, title: String, parent: Option[CategoryRow])

  final case class RawStaff(name: Option[String], email: Option[String], reports: List[RawStaff])
  final case class Email(value: String)
  final case class Staff(name: String, email: Email, reports: Vector[Staff])

  /** Every reply level is rendered by this same transformer, which counts direct replies. */
  given commentView: Transformer[Comment, CommentView] = Transformer
    .define[Comment, CommentView]
    .withFieldRenamed(_.body, _.text)
    .withFieldComputed(_.replyCount, _.replies.size)
    .buildTransformer

  /** Rule subtypes are renamed for storage; nested rules recurse into this transformer. */
  given storedRule: Transformer[Rule, StoredRule] = Transformer
    .define[Rule, StoredRule]
    .withSealedSubtypeRenamed[Keyword, KeywordNode]
    .withSealedSubtypeRenamed[AllOf, AllNode]
    .withSealedSubtypeRenamed[AnyOf, AnyNode]
    .withSealedSubtypeRenamed[Not, NotNode]
    .buildTransformer

  /** A category's ancestor chain is a recursive `Option`, mirrored structurally in the row. */
  given categoryRow: Transformer[Category, CategoryRow] = Transformer.derive[Category, CategoryRow]

  /** Addresses must carry a domain; the rest of the import is checked by derivation. */
  given PartialTransformer[String, Email] = PartialTransformer { raw =>
    if (raw.contains("@") && !raw.endsWith("@")) partial.Result.fromValue(Email(raw.toLowerCase))
    else partial.Result.fromErrorString(s"'$raw' is not an email address")
  }

  given staffImport: PartialTransformer[RawStaff, Staff] = PartialTransformer.derive[RawStaff, Staff]

  @Test
  def rendersEveryLevelOfACommentThread(): Unit = {
    val thread: Comment = Comment(
      1L,
      "ada",
      "Has anyone tried the new release?",
      List(
        Comment(2L, "linus", "Yes, it works well.", List(Comment(4L, "ada", "Thanks!", Nil))),
        Comment(3L, "grace", "Not yet.", Nil)
      )
    )

    val view: CommentView = thread.transformInto[CommentView]

    assertEquals(
      CommentView(
        1L,
        "ada",
        "Has anyone tried the new release?",
        2,
        Vector(
          CommentView(2L, "linus", "Yes, it works well.", 1, Vector(CommentView(4L, "ada", "Thanks!", 0, Vector.empty))),
          CommentView(3L, "grace", "Not yet.", 0, Vector.empty)
        )
      ),
      view
    )
  }

  @Test
  def rendersADeepReplyChainWithoutLosingLevels(): Unit = {
    val depth: Int = 200
    val chain: Comment = (depth to 1 by -1).foldLeft(Comment(0L, "root", "leaf", Nil)) { (child, id) =>
      Comment(id.toLong, s"user$id", s"reply $id", List(child))
    }

    val view: CommentView = chain.transformInto[CommentView]

    var levels: Int = 0
    var current: CommentView = view
    while (current.replies.nonEmpty) {
      assertEquals(1, current.replyCount)
      assertEquals(s"reply ${current.id}", current.text)
      current = current.replies.head
      levels += 1
    }
    assertEquals(depth, levels)
    assertEquals(CommentView(0L, "root", "leaf", 0, Vector.empty), current)
  }

  @Test
  def persistsANestedModerationRuleTree(): Unit = {
    val rule: Rule = AnyOf(
      List(
        Keyword("spam"),
        AllOf(List(Keyword("crypto"), Not(Keyword("verified")))),
        Not(Not(Keyword("banned")))
      )
    )

    val stored: StoredRule = rule.transformInto[StoredRule]

    assertEquals(
      AnyNode(
        Vector(
          KeywordNode("spam"),
          AllNode(Vector(KeywordNode("crypto"), NotNode(KeywordNode("verified")))),
          NotNode(NotNode(KeywordNode("banned")))
        )
      ),
      stored
    )
    assertEquals(Vector(NotNode(KeywordNode("x"))), List[Rule](Not(Keyword("x"))).transformInto[Vector[StoredRule]])
  }

  @Test
  def storesACategoryWithItsAncestorChain(): Unit = {
    val forum: Category = Category("forum", "Forum", None)
    val help: Category = Category("help", "Help", Some(forum))
    val install: Category = Category("install", "Installation", Some(help))

    val row: CategoryRow = install.transformInto[CategoryRow]

    assertEquals(
      CategoryRow("install", "Installation", Some(CategoryRow("help", "Help", Some(CategoryRow("forum", "Forum", None))))),
      row
    )
  }

  @Test
  def importsAValidStaffHierarchy(): Unit = {
    val raw: RawStaff = RawStaff(
      Some("Ada"),
      Some("Ada@Forum.org"),
      List(
        RawStaff(Some("Linus"), Some("linus@forum.org"), List(RawStaff(Some("Grace"), Some("grace@forum.org"), Nil))),
        RawStaff(Some("Alan"), Some("alan@forum.org"), Nil)
      )
    )

    val result: partial.Result[Staff] = raw.transformIntoPartial[Staff]

    assertEquals(
      Right(
        Staff(
          "Ada",
          Email("ada@forum.org"),
          Vector(
            Staff("Linus", Email("linus@forum.org"), Vector(Staff("Grace", Email("grace@forum.org"), Vector.empty))),
            Staff("Alan", Email("alan@forum.org"), Vector.empty)
          )
        )
      ),
      result.asEither.left.map(_.asErrorPathMessageStrings.toList)
    )
  }

  @Test
  def reportsImportErrorsAtTheirNestedPath(): Unit = {
    val raw: RawStaff = RawStaff(
      Some("Ada"),
      Some("ada@forum.org"),
      List(
        RawStaff(Some("Linus"), Some("linus@forum.org"), List(RawStaff(None, Some("grace@forum.org"), Nil))),
        RawStaff(Some("Alan"), Some("alan-at-forum"), Nil)
      )
    )

    val errors: List[(String, String)] = raw.transformIntoPartial[Staff].asErrorPathMessageStrings.toList

    assertEquals(
      List(
        "reports(0).reports(0).name" -> "empty value",
        "reports(1).email" -> "'alan-at-forum' is not an email address"
      ),
      errors
    )

    val firstOnly: List[(String, String)] =
      raw.transformIntoPartial[Staff](failFast = true).asErrorPathMessageStrings.toList
    assertEquals(1, firstOnly.size)
    assertTrue(errors.contains(firstOnly.head))
  }
}
