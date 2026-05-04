/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_lihaoyi.scalatags_3

import java.io.ByteArrayOutputStream
import java.io.StringWriter
import java.nio.charset.StandardCharsets

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import scalatags.Text.all._

class Scalatags_3Test {
  @Test
  def rendersDoctypeAndNestedHtmlWithEscapedContent(): Unit = {
    val document: String = doctype("html")(
      html(
        head(tag("title")("Scalatags & Native Image")),
        body(
          div(id := "app", cls := "layout primary", data("tracking-id") := "card-7")(
            h1("Hello <Scala> & GraalVM"),
            p("Text nodes are escaped before rendering")
          )
        )
      )
    ).render

    assertThat(document).isEqualTo(
      "<!DOCTYPE html>" +
        "<html><head><title>Scalatags &amp; Native Image</title></head>" +
        "<body><div id=\"app\" class=\"layout primary\" data-tracking-id=\"card-7\">" +
        "<h1>Hello &lt;Scala&gt; &amp; GraalVM</h1>" +
        "<p>Text nodes are escaped before rendering</p>" +
        "</div></body></html>"
    )
  }

  @Test
  def escapesAttributeValuesAndSupportsCustomAttributes(): Unit = {
    val markup: String = div(
      attr("aria-live") := "polite",
      attr("data-query") := "name=scala&kind=<tag>",
      a(href := "/search?q=scalatags&kind=<html>")("Search <results>")
    ).render

    assertThat(markup).isEqualTo(
      "<div aria-live=\"polite\" data-query=\"name=scala&amp;kind=&lt;tag&gt;\">" +
        "<a href=\"/search?q=scalatags&amp;kind=&lt;html&gt;\">Search &lt;results&gt;</a>" +
        "</div>"
    )
  }

  @Test
  def rendersRawFragmentsOnlyWhereRequested(): Unit = {
    val widget: String = tag("x-widget")(
      attr("mode") := "trusted",
      raw("<span class=\"trusted\">ready</span>"),
      " & waiting <safely>"
    ).render

    assertThat(widget).isEqualTo(
      "<x-widget mode=\"trusted\"><span class=\"trusted\">ready</span>" +
        " &amp; waiting &lt;safely&gt;</x-widget>"
    )
  }

  @Test
  def rendersVoidHtmlTagsAsSelfClosingTags(): Unit = {
    val markup: String = div(
      p("Line one", br, "Line two"),
      img(src := "/assets/logo.svg", alt := "Scalatags logo"),
      input(tpe := "email", name := "contact", value := "team@example.com")
    ).render

    assertThat(markup).isEqualTo(
      "<div><p>Line one<br />Line two</p>" +
        "<img src=\"/assets/logo.svg\" alt=\"Scalatags logo\" />" +
        "<input type=\"email\" name=\"contact\" value=\"team@example.com\" /></div>"
    )
  }

  @Test
  def rendersCollectionsAsRepeatedChildFragments(): Unit = {
    val rows: Seq[(String, Int)] = Seq("alpha" -> 1, "beta" -> 2, "gamma" -> 3)
    val tableMarkup: String = table(
      thead(tr(th("Name"), th("Count"))),
      tbody(
        rows.map { case (name: String, count: Int) =>
          tr(td(name), td(count.toString))
        }
      )
    ).render

    assertThat(tableMarkup).isEqualTo(
      "<table><thead><tr><th>Name</th><th>Count</th></tr></thead>" +
        "<tbody>" +
        "<tr><td>alpha</td><td>1</td></tr>" +
        "<tr><td>beta</td><td>2</td></tr>" +
        "<tr><td>gamma</td><td>3</td></tr>" +
        "</tbody></table>"
    )
  }

  @Test
  def rendersInlineStylesWithTypedPixelValues(): Unit = {
    val styled: String = div(
      backgroundColor := "rgb(250, 250, 250)",
      color := "#123456",
      fontSize := 16.px,
      padding := 8.px,
      margin := "0 auto"
    )("Styled block").render
    val compactStyle: String = styled.replace(" ", "")

    assertThat(styled).startsWith("<div style=\"")
    assertThat(compactStyle).contains("background-color:rgb(250,250,250)")
    assertThat(compactStyle).contains("color:#123456")
    assertThat(compactStyle).contains("font-size:16px")
    assertThat(compactStyle).contains("padding:8px")
    assertThat(compactStyle).contains("margin:0auto")
    assertThat(styled).endsWith("\">Styled block</div>")
  }

  @Test
  def rendersSvgTagsAndSvgSpecificAttributes(): Unit = {
    import scalatags.Text.svgAttrs.{cx, cy, fill, r, stroke, strokeWidth, viewBox, x, y}
    import scalatags.Text.svgTags.{circle, rect, svg}

    val image: String = svg(
      viewBox := "0 0 120 120",
      attr("width") := 120,
      attr("height") := 120,
      circle(cx := 60, cy := 60, r := 50, fill := "#eef"),
      rect(
        x := 20,
        y := 20,
        attr("width") := 80,
        attr("height") := 80,
        fill := "none",
        stroke := "#333",
        strokeWidth := 4
      )
    ).render

    assertThat(image).isEqualTo(
      "<svg viewBox=\"0 0 120 120\" width=\"120\" height=\"120\">" +
        "<circle cx=\"60\" cy=\"60\" r=\"50\" fill=\"#eef\"></circle>" +
        "<rect x=\"20\" y=\"20\" width=\"80\" height=\"80\" " +
        "fill=\"none\" stroke=\"#333\" stroke-width=\"4\"></rect>" +
        "</svg>"
    )
  }

  @Test
  def writesMarkupToCharacterAndByteStreams(): Unit = {
    val component: scalatags.Text.TypedTag[String] = form(
      h2("Streamed output"),
      ul(
        li("first"),
        li("second")
      )
    )
    val expectedMarkup: String =
      "<form><h2>Streamed output</h2><ul><li>first</li><li>second</li></ul></form>"

    val writer: StringWriter = new StringWriter()
    component.writeTo(writer)

    val output: ByteArrayOutputStream = new ByteArrayOutputStream()
    component.writeBytesTo(output)
    val byteStreamMarkup: String = new String(output.toByteArray, StandardCharsets.UTF_8)

    assertThat(writer.toString).isEqualTo(expectedMarkup)
    assertThat(byteStreamMarkup).isEqualTo(expectedMarkup)
    assertThat(component.httpContentType.contains("text/html")).isTrue()
  }
}
