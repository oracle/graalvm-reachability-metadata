/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_parser_3

import io.circe.{Decoder, DecodingFailure}
import io.circe.parser.decode
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/** Exercises recursive JSON tree decoding for importing a content-management navigation menu. */
@Timeout(60)
class NavigationTreeImportTest {
  private final case class MenuNode(title: String, path: Option[String], children: Vector[MenuNode])
  private final case class Route(path: String, breadcrumb: String)

  private given menuDecoder: Decoder[MenuNode] = Decoder.instance { cursor =>
    for {
      title <- cursor.get[String]("title")
      path <- cursor.get[Option[String]]("path")
      children <- cursor.getOrElse[Vector[MenuNode]]("children")(Vector.empty)(
        using Decoder.decodeVector[MenuNode](using menuDecoder)
      )
    } yield MenuNode(title, path, children)
  }

  private def routes(node: MenuNode, ancestors: Vector[String]): Vector[Route] = {
    val labels: Vector[String] = ancestors :+ node.title
    val ownRoute: Vector[Route] = node.path.toVector.map(path => Route(path, labels.mkString(" / ")))
    ownRoute ++ node.children.flatMap(child => routes(child, labels))
  }

  private val menuExport: String =
    """{
      |  "title": "Shop",
      |  "path": "/",
      |  "children": [
      |    {
      |      "title": "Products",
      |      "children": [
      |        {"title": "Books", "path": "/books"},
      |        {
      |          "title": "Stationery",
      |          "path": "/stationery",
      |          "children": [{"title": "Pens", "path": "/pens", "children": []}]
      |        }
      |      ]
      |    },
      |    {"title": "Help", "path": "/help"},
      |    {"title": "Coming soon", "children": []}
      |  ]
      |}""".stripMargin

  @Test
  def importsNestedGroupsAndBuildsOrderedRoutesWithFullBreadcrumbs(): Unit = {
    val tree: MenuNode = decode[MenuNode](menuExport).fold(error => throw error, identity)
    val index: Vector[Route] = routes(tree, Vector.empty)

    assertEquals(
      Vector(
        Route("/", "Shop"),
        Route("/books", "Shop / Products / Books"),
        Route("/stationery", "Shop / Products / Stationery"),
        Route("/pens", "Shop / Products / Stationery / Pens"),
        Route("/help", "Shop / Help")
      ),
      index
    )
    assertEquals(Vector.empty[MenuNode], tree.children.head.children.head.children)
    assertEquals(None, tree.children.last.path)
    assertEquals(Some("/pens"), tree.children.head.children(1).children.head.path)
  }

  @Test
  def rejectsAnInvalidDescendantBeforeReplacingThePublishedRouteIndex(): Unit = {
    val published: Vector[Route] = Vector(Route("/old", "Previous menu"))
    val invalidExport: String = menuExport.replace("\"path\": \"/pens\"", "\"path\": false")
    val replacement: Either[io.circe.Error, Vector[Route]] =
      decode[MenuNode](invalidExport).map(tree => routes(tree, Vector.empty))

    assertTrue(replacement.swap.exists(_.isInstanceOf[DecodingFailure]))
    assertEquals(published, replacement.getOrElse(published))

    val invalidChildren: String = menuExport.replace("\"children\": []", "\"children\": {}")
    assertTrue(decode[MenuNode](invalidChildren).isLeft)
  }
}
