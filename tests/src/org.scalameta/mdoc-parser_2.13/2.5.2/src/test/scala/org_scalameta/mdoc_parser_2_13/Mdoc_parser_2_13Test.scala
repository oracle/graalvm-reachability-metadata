/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalameta.mdoc_parser_2_13

import scala.jdk.CollectionConverters._

import mdoc.parser.CodeFence
import mdoc.parser.MarkdownPart
import mdoc.parser.ParserSettings
import mdoc.parser.Text
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class Mdoc_parser_2_13Test {
  @Test
  def parsesMarkdownIntoTextAndCodeFencePartsWithSourcePositions(): Unit = {
    val prefix: String = "before\n"
    val opening: String = "```scala mdoc:reset\n"
    val body: String = "val answer = 42\n"
    val closing: String = "```\n"
    val suffix: String = "after\n"
    val markdown: String = prefix + opening + body + closing + suffix

    val parts: Vector[MarkdownPart] = parse(markdown)

    assertThat(parts.asJava).hasSize(3)
    assertThat(parts.head).isInstanceOf(classOf[Text])
    assertThat(parts.head.asInstanceOf[Text].value).isEqualTo(prefix)
    assertThat(parts.head.posBeg).isEqualTo(0)
    assertThat(parts.head.posEnd).isEqualTo(prefix.length)

    val fence: CodeFence = parts(1).asInstanceOf[CodeFence]
    val openingStart: Int = prefix.length
    val infoStart: Int = openingStart + 3
    val infoEnd: Int = openingStart + opening.length
    val closingStart: Int = infoEnd + body.length

    assertThat(fence.posBeg).isEqualTo(openingStart)
    assertThat(fence.posEnd).isEqualTo(closingStart + closing.length)
    assertThat(fence.openBackticks.value).isEqualTo("```")
    assertThat(fence.openBackticks.posBeg).isEqualTo(openingStart)
    assertThat(fence.openBackticks.posEnd).isEqualTo(infoStart)
    assertThat(fence.info.value).isEqualTo("scala mdoc:reset\n")
    assertThat(fence.info.posBeg).isEqualTo(infoStart)
    assertThat(fence.info.posEnd).isEqualTo(infoEnd)
    assertThat(fence.body.value).isEqualTo("val answer = 42")
    assertThat(fence.body.posBeg).isEqualTo(infoEnd)
    assertThat(fence.body.posEnd).isEqualTo(closingStart - 1)
    assertThat(fence.closeBackticks.value).isEqualTo("\n```\n")
    assertThat(fence.closeBackticks.posBeg).isEqualTo(closingStart - 1)
    assertThat(fence.closeBackticks.posEnd).isEqualTo(closingStart + closing.length)

    assertThat(parts(2)).isInstanceOf(classOf[Text])
    assertThat(parts(2).asInstanceOf[Text].value).isEqualTo(suffix)
    assertThat(render(parts)).isEqualTo(markdown)
  }

  @Test
  def requiresIndentedCodeFencesToBeExplicitlyEnabled(): Unit = {
    val markdown: String = "  ```scala mdoc\n  println(1)\n  ```\n"

    val textOnlyParts: Vector[MarkdownPart] = parse(markdown, allowIndented = false)
    assertThat(textOnlyParts.asJava).hasSize(3)
    assertThat(textOnlyParts.forall(_.isInstanceOf[Text])).isTrue
    assertThat(render(textOnlyParts)).isEqualTo(markdown)

    val fencedParts: Vector[MarkdownPart] = parse(markdown, allowIndented = true)
    assertThat(fencedParts.asJava).hasSize(1)
    val fence: CodeFence = fencedParts.head.asInstanceOf[CodeFence]

    assertThat(fence.indent).isEqualTo(2)
    assertThat(fence.tag.value).isEqualTo("  ")
    assertThat(fence.hasBlankTag).isTrue
    assertThat(fence.openBackticks.value).isEqualTo("```")
    assertThat(fence.info.value).isEqualTo("scala mdoc\n")
    assertThat(fence.body.value).isEqualTo("println(1)")
    assertThat(fence.closeBackticks.value).isEqualTo("\n```\n")
    assertThat(render(fencedParts)).isEqualTo(markdown)
  }

  @Test
  def parsesLongerBacktickFencesWithoutClosingOnShorterFenceMarkers(): Unit = {
    val markdown: String =
      "````scala mdoc\n" +
        "val literal = \"\"\"\n" +
        "```not a closing fence\n" +
        "\"\"\"\n" +
        "````\n" +
        "after\n"

    val parts: Vector[MarkdownPart] = parse(markdown)

    assertThat(parts.asJava).hasSize(2)
    val fence: CodeFence = parts.head.asInstanceOf[CodeFence]
    assertThat(fence.openBackticks.value).isEqualTo("````")
    assertThat(fence.info.value).isEqualTo("scala mdoc\n")
    assertThat(fence.body.value).isEqualTo(
      "val literal = \"\"\"\n" +
        "```not a closing fence\n" +
        "\"\"\""
    )
    assertThat(fence.closeBackticks.value).isEqualTo("\n````\n")
    assertThat(parts(1).asInstanceOf[Text].value).isEqualTo("after\n")
    assertThat(render(parts)).isEqualTo(markdown)
  }

  @Test
  def acceptsClosingFenceMarkersWithTrailingWhitespace(): Unit = {
    val markdown: String = "```scala mdoc\nprintln(1)\n```   \t\noutside\n"

    val parts: Vector[MarkdownPart] = parse(markdown)

    assertThat(parts.asJava).hasSize(2)
    val fence: CodeFence = parts.head.asInstanceOf[CodeFence]
    assertThat(fence.openBackticks.value).isEqualTo("```")
    assertThat(fence.info.value).isEqualTo("scala mdoc\n")
    assertThat(fence.body.value).isEqualTo("println(1)")
    assertThat(fence.closeBackticks.value).isEqualTo("\n```   \t\n")
    assertThat(parts(1)).isInstanceOf(classOf[Text])
    assertThat(parts(1).asInstanceOf[Text].value).isEqualTo("outside\n")
    assertThat(render(parts)).isEqualTo(markdown)
  }

  @Test
  def recognizesMdocModesFromFenceInfo(): Unit = {
    assertThat(fenceWithInfo("scala mdoc").getMdocMode).isEqualTo(Some(""))
    assertThat(fenceWithInfo("scala mdoc\n").getMdocMode).isEqualTo(Some(""))
    assertThat(fenceWithInfo("scala mdoc:reset").getMdocMode).isEqualTo(Some("reset"))
    assertThat(fenceWithInfo("scala mdoc:crash fail-fast\n").getMdocMode).isEqualTo(Some("crash"))
    assertThat(fenceWithInfo("scala mdoc:nest:inner").getMdocMode).isEqualTo(Some("nest:inner"))
    assertThat(fenceWithInfo("scala mdoc-js").getMdocMode).isEqualTo(None)
    assertThat(fenceWithInfo("scala").getMdocMode).isEqualTo(None)
  }

  @Test
  def rendersUpdatedFenceInfoAndBodyWithOriginalIndentation(): Unit = {
    val markdown: String = "  ```scala mdoc\n  val x = 1\n  ```\n"
    val fence: CodeFence = parse(markdown, allowIndented = true).head.asInstanceOf[CodeFence]

    fence.newInfo = Some("scala mdoc:reset")
    fence.newBody = Some("val y = 2\nval z = y + 1")

    assertThat(render(Vector(fence))).isEqualTo(
      "  ```scala mdoc:reset\n" +
        "  val y = 2\n" +
        "  val z = y + 1\n" +
        "  ```\n"
    )
  }

  @Test
  def rendersWholeFenceReplacementWithComputedIndentation(): Unit = {
    val indentedMarkdown: String = "    ```scala mdoc\n    println(\"old\")\n    ```\n"
    val indentedFence: CodeFence = parse(indentedMarkdown, allowIndented = true).head.asInstanceOf[CodeFence]
    indentedFence.newPart = Some("println(\"new\")\nprintln(\"done\")\n")

    assertThat(render(Vector(indentedFence))).isEqualTo(
      "    println(\"new\")\n" +
        "    println(\"done\")\n"
    )

    val quotedMarkdown: String = "> ```scala mdoc\n> println(0)\n> ```\n"
    val quotedFence: CodeFence = parse(quotedMarkdown, allowIndented = true).head.asInstanceOf[CodeFence]
    quotedFence.newPart = Some("quoted replacement\n")

    assertThat(quotedFence.indent).isEqualTo(2)
    assertThat(quotedFence.tag.value).isEqualTo("> ")
    assertThat(quotedFence.hasBlankTag).isFalse
    assertThat(render(Vector(quotedFence))).isEqualTo("  quoted replacement\n")
  }

  @Test
  def turnsUnterminatedFenceAtEndOfInputIntoCodeFencePart(): Unit = {
    val markdown: String = "intro\n```scala mdoc\nprintln(1)\n"

    val parts: Vector[MarkdownPart] = parse(markdown)

    assertThat(parts.asJava).hasSize(2)
    assertThat(parts.head.asInstanceOf[Text].value).isEqualTo("intro\n")
    val fence: CodeFence = parts(1).asInstanceOf[CodeFence]
    assertThat(fence.openBackticks.value).isEqualTo("```")
    assertThat(fence.info.value).isEqualTo("scala mdoc\n")
    assertThat(fence.body.value).isEqualTo("println(1)")
    assertThat(fence.closeBackticks.value).isEqualTo("\n")
    assertThat(fence.posEnd).isEqualTo(markdown.length)
    assertThat(render(parts)).isEqualTo(markdown)
  }

  @Test
  def textDropLinePrefixUpdatesValueAndPreservesSourcePositions(): Unit = {
    val original: Text = Text("  alpha\n\n  beta\r\n  gamma")
    original.posBeg = 10
    original.posEnd = 35

    val dropped: Text = original.dropLinePrefix(2)

    assertThat(dropped.value).isEqualTo("alpha\n\nbeta\r\ngamma")
    assertThat(dropped.posBeg).isEqualTo(original.posBeg)
    assertThat(dropped.posEnd).isEqualTo(original.posEnd)
    assertThat(original.dropLinePrefix(0)).isSameAs(original)
  }

  @Test
  def codeFenceSupportsCaseClassOperationsAndDefaultTag(): Unit = {
    val fence: CodeFence = CodeFence(Text("````"), Text("scala mdoc:silent\n"), Text("1 + 1"), Text("\n````\n"))
    val copied: CodeFence = fence.copy(info = Text("scala mdoc\n"), body = Text("2 + 2"))

    assertThat(fence.tag.value).isEqualTo("")
    assertThat(fence.indent).isEqualTo(0)
    assertThat(fence.hasBlankTag).isTrue
    assertThat(fence.getMdocMode).isEqualTo(Some("silent"))
    assertThat(copied.openBackticks.value).isEqualTo("````")
    assertThat(copied.info.value).isEqualTo("scala mdoc\n")
    assertThat(copied.body.value).isEqualTo("2 + 2")
    assertThat(copied.closeBackticks.value).isEqualTo("\n````\n")
    assertThat(copied.productArity).isEqualTo(5)
    assertThat(copied.productElementNames.toVector.asJava)
      .containsExactly("openBackticks", "info", "body", "closeBackticks", "tag")
  }

  private def parse(markdown: String, allowIndented: Boolean = false): Vector[MarkdownPart] =
    MarkdownPart.parse(markdown, TestParserSettings(allowIndented)).toVector

  private def render(parts: Seq[MarkdownPart]): String = {
    val out: StringBuilder = new StringBuilder
    parts.foreach(_.renderToString(out))
    out.toString()
  }

  private def fenceWithInfo(info: String): CodeFence =
    CodeFence(Text("```"), Text(info), Text(""), Text("```"))
}

final case class TestParserSettings(allowCodeFenceIndented: Boolean) extends ParserSettings
