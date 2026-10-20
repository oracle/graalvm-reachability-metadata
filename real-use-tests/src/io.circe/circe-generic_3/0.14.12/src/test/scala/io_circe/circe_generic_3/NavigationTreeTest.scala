/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_generic_3

import io.circe.Codec
import io.circe.Json
import io.circe.generic.semiauto.deriveCodec
import io.circe.parser.decode
import io.circe.parser.parse
import io.circe.syntax.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/** Exercises recursive product codec derivation for a content site's navigation tree. */
@Timeout(60)
class NavigationTreeTest {
  final case class NavigationNode(title: String, path: Option[String], children: List[NavigationNode])

  object NavigationNode {
    given Codec.AsObject[NavigationNode] = deriveCodec[NavigationNode]
  }

  @Test
  def loadsEditsAndPublishesANestedNavigationTree(): Unit = {
    val incoming: String = """
      {
        "title": "Documentation",
        "path": null,
        "children": [
          {
            "title": "Guides",
            "path": null,
            "children": [
              {"title": "Installation", "path": "/guides/install", "children": []},
              {"title": "First project", "path": "/guides/first-project", "children": []}
            ]
          },
          {"title": "Reference", "path": "/reference", "children": []}
        ]
      }
      """
    val tree: NavigationNode = decode[NavigationNode](incoming).fold(
      error => throw new AssertionError(s"Navigation tree was rejected: $error"),
      identity
    )
    val expected: NavigationNode = NavigationNode("Documentation", None, List(
      NavigationNode("Guides", None, List(
        NavigationNode("Installation", Some("/guides/install"), Nil),
        NavigationNode("First project", Some("/guides/first-project"), Nil)
      )),
      NavigationNode("Reference", Some("/reference"), Nil)
    ))

    assertThat(tree).isEqualTo(expected)
    assertThat(linkPaths(tree)).isEqualTo(List(
      "/guides/install",
      "/guides/first-project",
      "/reference"
    ))

    val updated: NavigationNode = tree.copy(children = tree.children.map { section =>
      if (section.title == "Guides") {
        section.copy(children = section.children :+
          NavigationNode("Deployment", Some("/guides/deploy"), Nil))
      } else {
        section
      }
    })
    val published: Json = updated.asJson
    val publishedGuides: Json = published.hcursor.downField("children").downArray
      .downField("children").focus.getOrElse(throw new AssertionError("Missing guide links"))
    val expectedGuides: Json = parse("""
      [
        {"title": "Installation", "path": "/guides/install", "children": []},
        {"title": "First project", "path": "/guides/first-project", "children": []},
        {"title": "Deployment", "path": "/guides/deploy", "children": []}
      ]
      """).fold(error => throw new AssertionError(s"Invalid fixture: $error"), identity)

    assertThat(publishedGuides).isEqualTo(expectedGuides)
    assertThat(decode[NavigationNode](published.noSpaces)).isEqualTo(Right(updated))
    assertThat(linkPaths(updated)).isEqualTo(List(
      "/guides/install",
      "/guides/first-project",
      "/guides/deploy",
      "/reference"
    ))
    assertThat(linkPaths(tree).contains("/guides/deploy")).isFalse()
  }

  private def linkPaths(node: NavigationNode): List[String] =
    node.path.toList ++ node.children.flatMap(linkPaths)
}
