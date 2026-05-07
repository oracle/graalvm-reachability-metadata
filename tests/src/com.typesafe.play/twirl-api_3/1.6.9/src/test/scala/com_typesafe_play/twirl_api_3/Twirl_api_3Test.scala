/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.twirl_api_3

import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Date
import java.util.Optional

import scala.reflect.classTag

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import play.twirl.api.*
import play.twirl.api.utils.StringEscapeUtils

class Twirl_api_3Test {
  @Test
  def htmlFormatEscapesRawTextAndPreservesTrustedFragments(): Unit = {
    val unsafeText: String = "<p class=\"x\">Tom & 'Jerry'</p>"
    val escaped: Html = HtmlFormat.escape(unsafeText)
    val raw: Html = HtmlFormat.raw(unsafeText)
    val filled: Html = HtmlFormat.fill(Seq(Html("<header>"), escaped, Html("</header>")))

    assertEquals("&lt;p class=&quot;x&quot;&gt;Tom &amp; &#x27;Jerry&#x27;&lt;/p&gt;", escaped.body)
    assertEquals(unsafeText, raw.body)
    assertEquals("<header>&lt;p class=&quot;x&quot;&gt;Tom &amp; &#x27;Jerry&#x27;&lt;/p&gt;</header>", filled.body)
    assertEquals(MimeTypes.HTML, filled.contentType)
    assertEquals(filled.body, filled.toString)
    assertEquals(Html("<header>&lt;p class=&quot;x&quot;&gt;Tom &amp; &#x27;Jerry&#x27;&lt;/p&gt;</header>"), filled)
    assertEquals(Html("same").hashCode(), HtmlFormat.raw("same").hashCode())
    assertSame(HtmlFormat.empty, HtmlFormat.empty)
  }

  @Test
  def xmlJavaScriptAndTextFormatsApplyTheirOwnEscapingRules(): Unit = {
    val xmlInput: String = "<node attr=\"A&B\">ok\u0001</node>"
    val scriptInput: String = "'\"\\/\b\n\t\f\r\u0001 end"
    val textInput: String = "plain <not escaped> & kept"

    assertEquals("&lt;node attr=&quot;A&amp;B&quot;&gt;ok&lt;/node&gt;", XmlFormat.escape(xmlInput).body)
    assertEquals(xmlInput, XmlFormat.raw(xmlInput).body)
    assertEquals("\\'\\\"\\\\\\/\\b\\n\\t\\f\\r end", JavaScriptFormat.escape(scriptInput).body)
    assertEquals(scriptInput, JavaScriptFormat.raw(scriptInput).body)
    assertEquals(textInput, TxtFormat.escape(textInput).body)
    assertEquals(textInput, TxtFormat.raw(textInput).body)

    assertEquals("<a/>&lt;b/&gt;", XmlFormat.fill(Seq(XmlFormat.raw("<a/>"), XmlFormat.escape("<b/>"))).body)
    assertEquals("var x = \\\"1\\\";", JavaScriptFormat.fill(Seq(JavaScriptFormat.raw("var x = "), JavaScriptFormat.escape("\"1\";"))).body)
    assertEquals("firstsecond", TxtFormat.fill(Seq(Txt("first"), Txt("second"))).body)

    assertEquals(MimeTypes.XML, Xml("<root/>").contentType)
    assertEquals(MimeTypes.JAVASCRIPT, JavaScript("alert(1)").contentType)
    assertEquals(MimeTypes.TEXT, Txt("hello").contentType)
    assertNotEquals(Html("same"), Xml("same"))
  }

  @Test
  def stringInterpolationEscapesValuesButKeepsSameFormatContentSafe(): Unit = {
    val dangerous: String = "<script>alert('x')</script>"
    val safeHtml: Html = Html("<strong>safe</strong>")
    val safeXml: Xml = Xml("<safe/>")
    val safeScript: JavaScript = JavaScript("alreadySafe()")

    val renderedHtml: Html = html"<div>$dangerous $safeHtml</div>"
    val renderedXml: Xml = xml"<root attr=$dangerous>$safeXml</root>"
    val renderedScript: JavaScript = js"const value = '$dangerous'; $safeScript"

    assertEquals("<div>&lt;script&gt;alert(&#x27;x&#x27;)&lt;/script&gt; <strong>safe</strong></div>", renderedHtml.body)
    assertEquals("<root attr=&lt;script&gt;alert('x')&lt;/script&gt;><safe/></root>", renderedXml.body)
    assertEquals("const value = '<script>alert(\\'x\\')<\\/script>'; alreadySafe()", renderedScript.body)
  }

  @Test
  def baseScalaTemplateDisplaysGeneratedTemplateValues(): Unit = {
    val template: BaseScalaTemplate[Html, HtmlFormat.type] = new BaseScalaTemplate(HtmlFormat)
    def display(value: Any): Html = template._display_(value)(classTag[Html])

    assertEquals("", display((): Unit).body)
    assertEquals("", display(null).body)
    assertEquals("", display(None).body)
    assertEquals("", display(Optional.empty[String]()).body)
    assertEquals("<safe>", display(Html("<safe>")).body)
    assertEquals("&lt;unsafe&gt;", display("<unsafe>").body)
    assertEquals("&lt;value&gt;", display(Some("<value>")).body)
    assertEquals("<strong>safe</strong>", display(Some(Html("<strong>safe</strong>"))).body)
    assertEquals("java &amp; scala", display(Optional.of("java & scala")).body)
    assertEquals("a&lt;b<i>c</i>", display(Seq("a<b", Html("<i>c</i>"))).body)
    assertEquals("xy", display(Iterator("x", "y")).body)
    assertEquals("&lt;one&gt;<two>", display(Array("<one>", Html("<two>"))).body)
    assertEquals("j&amp;list<ok>", display(Arrays.asList("j&list", Html("<ok>"))).body)
    assertEquals("42", display(42).body)

    val copied: BaseScalaTemplate[Html, HtmlFormat.type] = template.copy(HtmlFormat)
    assertEquals(template, copied)
    assertEquals("BaseScalaTemplate", copied.productPrefix)
    assertEquals(1, copied.productArity)
    assertEquals(HtmlFormat, copied.productElement(0))
    assertEquals("format", copied.productElementName(0))
  }

  @Test
  def contentConstructorsRenderOptionalAndNestedContent(): Unit = {
    val emptyFromOption: Html = Html(Option.empty[String])
    val trustedFromOption: Html = Html(Some("<strong>trusted</strong>"))
    val nested: Html = new Html(Seq(Html("<p>Hello "), trustedFromOption, Html("</p>")))

    assertEquals("", emptyFromOption.body)
    assertEquals(HtmlFormat.empty, emptyFromOption)
    assertEquals("<strong>trusted</strong>", trustedFromOption.body)
    assertEquals(MimeTypes.HTML, trustedFromOption.contentType)
    assertEquals("<p>Hello <strong>trusted</strong></p>", nested.body)
    assertEquals(nested.body, nested.toString)
  }

  @Test
  def templateInterfacesCanBeImplementedLikeGeneratedTemplates(): Unit = {
    val index: Template0[Html] = new Template0[Html] {
      override def render(): Html = html"<h1>${"Welcome & hello"}</h1>"
    }
    val greeting: Template1[String, Html] = new Template1[String, Html] {
      override def render(name: String): Html = html"<p>Hello $name</p>"
    }
    val pair: Template2[String, Int, Txt] = new Template2[String, Int, Txt] {
      override def render(label: String, count: Int): Txt = TxtFormat.fill(Seq(Txt(label), Txt(":"), Txt(count.toString)))
    }
    val row: Template3[String, String, Int, Html] = new Template3[String, String, Int, Html] {
      override def render(tag: String, label: String, count: Int): Html = html"<$tag>$label=$count</$tag>"
    }
    val manyArguments: Template22[Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Txt] =
      new Template22[Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Txt] {
        override def render(
            a1: Int,
            a2: Int,
            a3: Int,
            a4: Int,
            a5: Int,
            a6: Int,
            a7: Int,
            a8: Int,
            a9: Int,
            a10: Int,
            a11: Int,
            a12: Int,
            a13: Int,
            a14: Int,
            a15: Int,
            a16: Int,
            a17: Int,
            a18: Int,
            a19: Int,
            a20: Int,
            a21: Int,
            a22: Int): Txt = {
          val total: Int = Seq(a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19, a20, a21, a22).sum
          Txt(total.toString)
        }
      }

    assertContent(index.render(), "<h1>Welcome &amp; hello</h1>", MimeTypes.HTML)
    assertContent(greeting.render("<Ada>"), "<p>Hello &lt;Ada&gt;</p>", MimeTypes.HTML)
    assertContent(pair.render("items", 3), "items:3", MimeTypes.TEXT)
    assertContent(row.render("span", "total & tax", 7), "<span>total &amp; tax=7</span>", MimeTypes.HTML)
    assertContent(manyArguments.render(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1), "22", MimeTypes.TEXT)
  }

  @Test
  def helperAndFeatureImportsExposeTwirlRuntimeConveniences(): Unit = {
    val fallback = TwirlFeatureImports.TwirlDefaultValue("fallback")
    val date: Date = new Date(123456789L)
    val datePattern: String = "yyyy-MM-dd HH:mm"

    assertEquals("fallback", fallback.`?:`(""))
    assertEquals("fallback", fallback.`?:`(Nil))
    assertEquals("fallback", fallback.`?:`(false))
    assertEquals("fallback", fallback.`?:`(0))
    assertEquals("fallback", fallback.`?:`(None))
    assertEquals("actual", fallback.`?:`("actual"))
    assertEquals("kept", TwirlFeatureImports.using("kept"))
    assertEquals("value:5", TwirlFeatureImports.defining(5)(value => s"value:$value"))

    assertFalse(TwirlFeatureImports.twirlStringToBoolean(""))
    assertFalse(TwirlFeatureImports.twirlStringToBoolean(null))
    assertTrue(TwirlFeatureImports.twirlStringToBoolean("text"))
    assertFalse(TwirlFeatureImports.twirlOptionToBoolean(None))
    assertTrue(TwirlFeatureImports.twirlOptionToBoolean(Some(1)))
    assertFalse(TwirlFeatureImports.twirlIterableToBoolean(Nil))
    assertTrue(TwirlFeatureImports.twirlIterableToBoolean(List(1)))

    assertEquals("visible", TwirlHelperImports.TwirlRichString("visible").when(true))
    assertEquals("", TwirlHelperImports.TwirlRichString("hidden").when(false))
    assertEquals(new SimpleDateFormat(datePattern).format(date), TwirlHelperImports.TwirlRichDate(date).format(datePattern))
    assertEquals(List("a", "b"), TwirlHelperImports.twirlJavaCollectionToScala(Arrays.asList("a", "b")).toList)
  }

  @Test
  def escapingUtilitiesAndSafeHandleControlCharactersAndNulls(): Unit = {
    assertEquals("", Formats.safe(null))
    assertEquals("trusted", Formats.safe("trusted"))
    assertEquals("a&lt;b&gt;&amp;&quot;\n\r\t", StringEscapeUtils.escapeXml11("a<b>&\"\u0000\n\r\t"))
    assertEquals("quote\\\" slash\\/ line\\n tab\\t", StringEscapeUtils.escapeEcmaScript("quote\" slash/ line\n tab\t"))
  }

  @Test
  def stringInterpolationSupportsUserDefinedFormats(): Unit = {
    final case class BracketedContent(override val body: String) extends Appendable[BracketedContent] with Content {
      override val contentType: String = "text/bracketed"
    }

    object BracketedFormat extends Format[BracketedContent] {
      override def raw(text: String): BracketedContent = BracketedContent(Formats.safe(text))

      override def escape(text: String): BracketedContent = BracketedContent(s"[${Formats.safe(text)}]")

      override val empty: BracketedContent = BracketedContent("")

      override def fill(elements: Seq[BracketedContent]): BracketedContent = BracketedContent(elements.map(_.body).mkString)
    }

    val trusted: BracketedContent = BracketedContent("<trusted>")
    val rendered: BracketedContent = StringContext("name=", ", trusted=", ", count=", "")
      .interpolate(Seq("Ada & Bob", trusted, 3), BracketedFormat)

    assertContent(rendered, "name=[Ada & Bob], trusted=<trusted>, count=[3]", "text/bracketed")
  }

  private def assertContent(content: Content, expectedBody: String, expectedContentType: String): Unit = {
    assertEquals(expectedBody, content.body)
    assertEquals(expectedContentType, content.contentType)
  }
}
