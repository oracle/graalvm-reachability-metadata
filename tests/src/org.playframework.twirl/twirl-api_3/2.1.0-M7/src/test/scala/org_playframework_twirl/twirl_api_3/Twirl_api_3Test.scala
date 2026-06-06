/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework_twirl.twirl_api_3

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import play.twirl.api.*
import play.twirl.api.utils.StringEscapeUtils

import java.util.Date
import java.util.Optional
import scala.reflect.ClassTag

class Twirl_api_3Test {
  private final case class Markdown(body: String) extends play.twirl.api.Appendable[Markdown]

  private object MarkdownFormat extends Format[Markdown] {
    override def raw(text: String): Markdown = Markdown(Formats.safe(text))

    override def escape(text: String): Markdown = {
      val safeText: String = Formats.safe(text)
      Markdown(safeText.replace("\\", "\\\\").replace("*", "\\*").replace("_", "\\_"))
    }

    override def empty: Markdown = Markdown("")

    override def fill(elements: Seq[Markdown]): Markdown = Markdown(elements.map(_.body).mkString)
  }

  @Test
  def escapesAndRendersBuiltInFormats(): Unit = {
    assertEquals("", Formats.safe(null))
    assertEquals("already safe", Formats.safe("already safe"))

    val html: Html = HtmlFormat.escape("<div title=\"Tom & Jerry's\">safe</div>")
    assertEquals("&lt;div title=&quot;Tom &amp; Jerry&#x27;s&quot;&gt;safe&lt;/div&gt;", html.body)
    assertEquals(html.body, html.toString)
    assertEquals(MimeTypes.HTML, html.contentType)
    assertEquals("<strong>trusted</strong>", HtmlFormat.raw("<strong>trusted</strong>").body)
    assertEquals("", Html(None).body)
    assertEquals("from option", Html(Some("from option")).body)

    val xml: Xml = XmlFormat.escape("<node attr=\"value&more\">text</node>")
    assertEquals("&lt;node attr=&quot;value&amp;more&quot;&gt;text&lt;/node&gt;", xml.body)
    assertEquals(MimeTypes.XML, xml.contentType)
    assertEquals("plain <xml>", XmlFormat.raw("plain <xml>").body)

    val javaScript: JavaScript = JavaScriptFormat.escape("if (path == '/x') { alert(\"Hi\n\"); }")
    assertEquals("if (path == \\'\\/x\\') { alert(\\\"Hi\\n\\\"); }", javaScript.body)
    assertEquals(MimeTypes.JAVASCRIPT, javaScript.contentType)
    assertEquals("alert('raw')", JavaScriptFormat.raw("alert('raw')").body)

    val text: Txt = TxtFormat.escape("text is not escaped: <>&\"")
    assertEquals("text is not escaped: <>&\"", text.body)
    assertEquals(MimeTypes.TEXT, text.contentType)
  }

  @Test
  def concatenatesBufferedContentAndPreservesValueSemantics(): Unit = {
    val html: Html = HtmlFormat.fill(Seq(
      HtmlFormat.raw("<p>"),
      HtmlFormat.escape("one & two"),
      HtmlFormat.raw("</p>")
    ))
    assertEquals("<p>one &amp; two</p>", html.body)
    assertEquals(html.body, html.toString)

    val equalHtml: Html = new Html(Seq(
      HtmlFormat.raw("<p>"),
      HtmlFormat.escape("one & two"),
      HtmlFormat.raw("</p>")
    ))
    assertEquals(equalHtml, html)
    assertEquals(equalHtml.hashCode, html.hashCode)
    assertNotEquals(TxtFormat.raw(html.body), html)

    assertEquals("abc", TxtFormat.fill(Seq(Txt("a"), Txt("b"), Txt("c"))).body)
    assertEquals("<a/>&lt;b/&gt;", XmlFormat.fill(Seq(XmlFormat.raw("<a/>"), XmlFormat.escape("<b/>"))).body)
    assertEquals("var s = \\\"x\\\";", JavaScriptFormat.fill(Seq(
      JavaScriptFormat.raw("var s = "),
      JavaScriptFormat.escape("\"x\""),
      JavaScriptFormat.raw(";")
    )).body)
  }

  @Test
  def displaysTemplateValuesWithTwirlRules(): Unit = {
    val template: BaseScalaTemplate[Html, Format[Html]] = new BaseScalaTemplate[Html, Format[Html]](HtmlFormat)
    val htmlClassTag: ClassTag[Html] = summon[ClassTag[Html]]

    assertSame(HtmlFormat, template.`$twirl__format`)
    assertEquals(1, template.productArity)
    assertEquals("$twirl__format", template.productElementName(0))
    assertSame(HtmlFormat, template.productElement(0))
    assertThrows(classOf[IndexOutOfBoundsException], () => template.productElement(1))

    assertEquals("&lt;unsafe&gt;", template._display_("<unsafe>").body)
    assertEquals("42", template._display_(42).body)
    assertEquals("", template._display_(null.asInstanceOf[String]).body)
    assertEquals("", template._display_(scala.runtime.BoxedUnit.UNIT).body)
    assertEquals("<b>safe</b>", template._display_(HtmlFormat.raw("<b>safe</b>")).body)

    assertEquals("optional &amp; escaped", template._display_(Some("optional & escaped"))(htmlClassTag).body)
    assertEquals("", template._display_(None)(htmlClassTag).body)
    assertEquals("java &lt;optional&gt;", template._display_(Optional.of("java <optional>"))(htmlClassTag).body)
    assertEquals("", template._display_(Optional.empty[String]())(htmlClassTag).body)

    assertEquals("a&amp;<b>b</b>c&lt;", template._display_(Seq("a&", HtmlFormat.raw("<b>b</b>"), "c<"))(htmlClassTag).body)
    assertEquals("x&lt;y&gt;", template._display_(Array("x<", "y>"))(htmlClassTag).body)
    assertEquals("j&lt;<em>safe</em>", template._display_(java.util.Arrays.asList[Any]("j<", HtmlFormat.raw("<em>safe</em>")))(htmlClassTag).body)

    val copied: BaseScalaTemplate[Html, Format[Html]] = template.copy(HtmlFormat)
    assertEquals(template, copied)
    assertEquals(template.hashCode, copied.hashCode)
    assertEquals("BaseScalaTemplate", template.productPrefix)
  }

  @Test
  def rendersScalaXmlNodeSequencesAsRawTemplateContent(): Unit = {
    val template: BaseScalaTemplate[Html, Format[Html]] = new BaseScalaTemplate[Html, Format[Html]](HtmlFormat)
    val node: scala.xml.Elem = new scala.xml.Elem(
      null,
      "span",
      scala.xml.Null,
      scala.xml.TopScope,
      false,
      new scala.xml.Text("Tom & Jerry")
    )
    val nodes: scala.xml.NodeSeq = node

    assertEquals("<span>Tom &amp; Jerry</span>", template._display_(nodes).body)
    assertEquals("", template._display_(scala.xml.NodeSeq.Empty).body)
  }

  @Test
  def stringInterpolatorsEscapeArgumentsAndKeepAppendableValuesRaw(): Unit = {
    val person: String = "<Ada & Bob>"
    val trustedHtml: Html = HtmlFormat.raw("<em>trusted</em>")
    val htmlResult: Html = html"<p>$person $trustedHtml</p>"
    assertEquals("<p>&lt;Ada &amp; Bob&gt; <em>trusted</em></p>", htmlResult.body)

    val xmlValue: String = "<node attr=\"unsafe&value\"/>"
    val xmlResult: Xml = xml"<root>$xmlValue</root>"
    assertEquals("<root>&lt;node attr=&quot;unsafe&amp;value&quot;/&gt;</root>", xmlResult.body)

    val scriptValue: String = "'quoted'\n/ slash"
    val scriptResult: JavaScript = js"$scriptValue"
    assertEquals("\\'quoted\\'\\n\\/ slash", scriptResult.body)
  }

  @Test
  def genericStringInterpolationSupportsCustomFormats(): Unit = {
    given Format[Markdown] = MarkdownFormat

    val trustedText: Markdown = MarkdownFormat.raw("**trusted**")
    val renderedText: Markdown =
      StringContext("Hello ", " and ", "!").interpolate[Markdown](
        Seq("A_B *C*", trustedText),
        MarkdownFormat
      )(summon[ClassTag[Markdown]])

    assertEquals("Hello A\\_B \\*C\\* and **trusted**!", renderedText.body)
  }

  @Test
  def helperAndFeatureImportsSupportTemplateIdioms(): Unit = {
    assertEquals("PLAY", TwirlFeatureImports.defining("play")(_.toUpperCase))

    val marker: AnyRef = new Object()
    assertSame(marker, TwirlFeatureImports.using(marker))

    assertTrue(TwirlFeatureImports.twirlStringToBoolean("value"))
    assertFalse(TwirlFeatureImports.twirlStringToBoolean(""))
    assertFalse(TwirlFeatureImports.twirlStringToBoolean(null))
    assertTrue(TwirlFeatureImports.twirlOptionToBoolean(Some("value")))
    assertFalse(TwirlFeatureImports.twirlOptionToBoolean(None))
    assertFalse(TwirlFeatureImports.twirlOptionToBoolean(null))
    assertTrue(TwirlFeatureImports.twirlIterableToBoolean(List(1)))
    assertFalse(TwirlFeatureImports.twirlIterableToBoolean(List.empty[Int]))
    assertFalse(TwirlFeatureImports.twirlIterableToBoolean(null))

    val defaultValue: TwirlFeatureImports.TwirlDefaultValue = TwirlFeatureImports.TwirlDefaultValue("fallback")
    assertEquals("fallback", defaultValue.?:(""))
    assertEquals("fallback", defaultValue.?:(Nil))
    assertEquals("fallback", defaultValue.?:(false))
    assertEquals("fallback", defaultValue.?:(0))
    assertEquals("fallback", defaultValue.?:(None))
    assertEquals("actual", defaultValue.?:("actual"))
    assertEquals(Some("actual"), defaultValue.?:(Some("actual")))

    assertEquals("visible", TwirlHelperImports.TwirlRichString("visible").when(true))
    assertEquals("", TwirlHelperImports.TwirlRichString("hidden").when(false))
    assertEquals("1970", TwirlHelperImports.TwirlRichDate(new Date(15L * 60L * 60L * 1000L)).format("yyyy"))

    val scalaIterable: Iterable[String] = TwirlHelperImports.twirlJavaCollectionToScala(java.util.Arrays.asList("a", "b"))
    assertEquals(List("a", "b"), scalaIterable.toList)
  }

  @Test
  def templateInterfacesRenderConcreteResults(): Unit = {
    val template0: Template0[Txt] = new Template0[Txt] {
      override def render(): Txt = TxtFormat.raw("static")
    }
    val template1: Template1[String, Html] = new Template1[String, Html] {
      override def render(name: String): Html = HtmlFormat.escape(name)
    }
    val template2: Template2[String, Int, Txt] = new Template2[String, Int, Txt] {
      override def render(label: String, count: Int): Txt = TxtFormat.raw(s"$label=$count")
    }

    assertEquals("static", template0.render().body)
    assertEquals("&lt;user&gt;", template1.render("<user>").body)
    assertEquals("items=3", template2.render("items", 3).body)
  }

  @Test
  def escapeUtilitiesRemoveUnsupportedControlCharacters(): Unit = {
    assertEquals("line\\nbreak\\tand\\\\slash", StringEscapeUtils.escapeEcmaScript("line\nbreak\tand\\slash\u0001"))
    assertEquals("&lt;tag attr=&quot;value&amp;more&quot;&gt;\ntext", StringEscapeUtils.escapeXml11("<tag attr=\"value&more\">\ntext\u0001"))
  }
}
