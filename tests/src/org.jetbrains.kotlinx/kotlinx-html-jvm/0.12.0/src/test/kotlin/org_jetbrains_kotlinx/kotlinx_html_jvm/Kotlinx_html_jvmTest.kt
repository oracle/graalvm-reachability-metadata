/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_html_jvm

import kotlinx.html.*
import kotlinx.html.attributesMapOf
import kotlinx.html.attributes.enumEncode
import kotlinx.html.attributes.stringSetDecode
import kotlinx.html.attributes.stringSetEncode
import kotlinx.html.dom.append
import kotlinx.html.dom.createHTMLDocument
import kotlinx.html.dom.prepend
import kotlinx.html.dom.serialize
import kotlinx.html.stream.appendHTML
import kotlinx.html.stream.createHTML
import kotlinx.html.consumers.delayed
import kotlinx.html.consumers.filter
import kotlinx.html.consumers.onFinalizeMap
import kotlinx.html.consumers.trace
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

public class Kotlinx_html_jvmTest {
    @Test
    fun rendersFullHtmlDocumentWithEscapingAttributesEntitiesAndUnsafeContent() {
        val rendered: String = createHTML(prettyPrint = false).html {
            lang = "en"
            dir = Dir.ltr
            head {
                title { +"Kotlinx <HTML> & tests" }
                style(type = "text/css") {
                    unsafe {
                        raw("body > main { color: #123; }")
                    }
                }
                script(type = "application/javascript") {
                    crossorigin = ScriptCrossorigin.anonymous
                    unsafe {
                        raw("window.ready = true;")
                    }
                }
            }
            body {
                id = "page"
                classes = linkedSetOf("layout", "theme-dark")
                attributes["data-kind"] = "sample & integration"
                h1 {
                    +"High coverage"
                    +Entities.nbsp
                    span { +"DSL" }
                }
                p {
                    title = "5 < 7 & \"quoted\""
                    +"Tom & Jerry <script>"
                    br { }
                    comment("kept as an html comment")
                    unsafe {
                        raw("<span class=\"raw\">trusted</span>")
                    }
                }
            }
        }

        assertTrue(rendered.startsWith("<html"))
        assertTrue(rendered.contains("lang=\"en\""))
        assertTrue(rendered.contains("dir=\"ltr\""))
        assertTrue(rendered.contains("<title>Kotlinx &lt;HTML&gt; &amp; tests</title>"))
        assertTrue(rendered.contains("body > main { color: #123; }"))
        assertTrue(rendered.contains("crossorigin=\"anonymous\""))
        assertTrue(rendered.contains("class=\"layout theme-dark\""))
        assertTrue(rendered.contains("data-kind=\"sample &amp; integration\""))
        assertTrue(rendered.contains("High coverage&nbsp;<span>DSL</span>"))
        assertTrue(rendered.contains("title=\"5 &lt; 7 &amp; &quot;quoted&quot;\""))
        assertTrue(rendered.contains("Tom &amp; Jerry &lt;script&gt;"))
        assertTrue(rendered.contains("kept as an html comment"))
        assertTrue(rendered.contains("<span class=\"raw\">trusted</span>"))
    }

    @Test
    fun rendersFormsListsTablesImagesAndEventAttributes() {
        val rendered: String = createHTML(prettyPrint = false).body {
            form(action = "/submit", method = FormMethod.post) {
                id = "survey"
                fieldSet {
                    label {
                        htmlFor = "email"
                        +"Email"
                    }
                    input(type = InputType.email) {
                        id = "email"
                        name = "email"
                        required = true
                        placeholder = "team@example.test"
                    }
                    hiddenInput(name = "token") {
                        value = "abc123"
                    }
                    select {
                        name = "choice"
                        required = true
                        option {
                            value = "kotlin"
                            selected = true
                            +"Kotlin"
                        }
                        option {
                            value = "native"
                            +"Native Image"
                        }
                    }
                    textArea {
                        name = "notes"
                        rows = "4"
                        cols = "30"
                        wrap = TextAreaWrap.soft
                        +"Leave <notes> here"
                    }
                    button(type = ButtonType.submit) {
                        onClick = "return validateForm()"
                        +"Send"
                    }
                }
            }
            ul {
                li { +"first" }
                li { +"second" }
            }
            table {
                thead {
                    tr {
                        th { +"Name" }
                        th { +"Score" }
                    }
                }
                tbody {
                    tr {
                        td { +"Ada" }
                        td { +"42" }
                    }
                }
            }
            a(href = "https://example.test?q=1&x=2", referrerPolicy = AReferrerPolicy.noReferrer) {
                target = "_blank"
                +"external link"
            }
            img(src = "/logo.svg", alt = "Logo", loading = ImgLoading.lazy) { }
        }

        assertTrue(rendered.contains("<form"))
        assertTrue(rendered.contains("action=\"/submit\""))
        assertTrue(rendered.contains("method=\"post\""))
        assertTrue(rendered.contains("type=\"email\""))
        assertTrue(rendered.contains("required"))
        assertTrue(rendered.contains("type=\"hidden\""))
        assertTrue(rendered.contains("selected"))
        assertTrue(rendered.contains("Leave &lt;notes&gt; here"))
        assertTrue(rendered.contains("onclick=\"return validateForm()\""))
        assertTrue(rendered.contains("<ul><li>first</li><li>second</li></ul>"))
        assertTrue(rendered.contains("<table><thead><tr><th>Name</th><th>Score</th></tr></thead>"))
        assertTrue(rendered.contains("<tbody>"))
        assertTrue(rendered.contains("<td>Ada</td>"))
        assertTrue(rendered.contains("<td>42</td>"))
        assertTrue(rendered.contains("</table>"))
        assertTrue(rendered.contains("href=\"https://example.test?q=1&amp;x=2\""))
        assertTrue(rendered.contains("referrerpolicy=\"no-referrer\""))
        assertTrue(rendered.contains("loading=\"lazy\""))
    }

    @Test
    fun appendsHtmlToExistingAppendableAndReturnsTheSameAppendable() {
        val builder: StringBuilder = StringBuilder("prefix:")
        val returned: StringBuilder = builder.appendHTML(prettyPrint = false).div {
            id = "container"
            p { +"streamed" }
        }

        assertTrue(returned === builder)
        assertEquals("prefix:<div id=\"container\"><p>streamed</p></div>", builder.toString())
    }

    @Test
    fun consumerWrappersCanDelayTraceFilterAndMapFinalizedOutput() {
        val traceEvents: MutableList<String> = mutableListOf()
        val rendered: String = createHTML(prettyPrint = false)
            .filter { tag ->
                if (tag.tagName == "script") DROP else PASS
            }
            .trace { event: String -> traceEvents.add(event) }
            .delayed()
            .onFinalizeMap { html: String, partial: Boolean ->
                assertFalse(partial)
                "wrapped[$html]"
            }
            .div {
                p { +"visible" }
                script {
                    unsafe {
                        raw("hidden()")
                    }
                }
                span { +"kept" }
            }

        assertEquals("wrapped[<div><p>visible</p><span>kept</span></div>]", rendered)
        assertTrue(traceEvents.any { event: String -> event.contains("div") })
        assertTrue(traceEvents.any { event: String -> event.contains("p") })
        assertFalse(traceEvents.any { event: String -> event.contains("hidden") })
    }

    @Test
    fun domBuilderCreatesElementsAndCanSerializeThem() {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
        val elements = document.append {
            div {
                id = "root"
                attributes["data-source"] = "dom"
                p { +"DOM & stream compatible" }
            }
        }

        assertEquals(1, elements.size)
        val root = elements.single()
        assertEquals("div", root.tagName)
        assertEquals("root", root.getAttribute("id"))
        assertEquals("dom", root.getAttribute("data-source"))
        assertEquals("DOM & stream compatible", root.textContent)

        val serialized: String = root.serialize(prettyPrint = false)
        assertTrue(serialized.contains("<div"))
        assertTrue(serialized.contains("id=\"root\""))
        assertTrue(serialized.contains("data-source=\"dom\""))
        assertTrue(serialized.contains("DOM &amp; stream compatible"))
    }

    @Test
    fun domDocumentConsumerCreatesCompleteDocumentAndSerializesDoctype() {
        val document = createHTMLDocument().html {
            head {
                meta(charset = "utf-8")
                title { +"Generated DOM document" }
            }
            body {
                main {
                    id = "content"
                    p { +"DOM document & serialization" }
                }
            }
        }

        assertEquals("html", document.documentElement.tagName)
        assertEquals("Generated DOM document", document.getElementsByTagName("title").item(0).textContent)

        val meta: Element = document.getElementsByTagName("meta").item(0) as Element
        assertEquals("utf-8", meta.getAttribute("charset"))
        assertEquals("DOM document & serialization", document.getElementsByTagName("p").item(0).textContent)

        val main: Element = document.getElementsByTagName("main").item(0) as Element
        assertEquals("content", main.getAttribute("id"))

        val serialized: String = document.serialize(prettyPrint = false)
        assertTrue(serialized.startsWith("<!DOCTYPE html>"))
        assertTrue(serialized.contains("<title>Generated DOM document</title>"))
        assertTrue(serialized.contains("DOM document &amp; serialization"))
    }

    @Test
    fun domPrependInsertsGeneratedElementBeforeExistingChildren() {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
        val section = document.createElement("section")
        val existingParagraph = document.createElement("p")
        existingParagraph.textContent = "already present"
        section.appendChild(existingParagraph)
        document.appendChild(section)

        val elements = section.prepend {
            article {
                id = "intro"
                h2 { +"Prepended" }
                p { +"Inserted before existing DOM children" }
            }
        }

        assertEquals(1, elements.size)
        val inserted = elements.single()
        assertEquals("article", inserted.tagName)
        assertEquals("intro", inserted.getAttribute("id"))
        assertTrue(section.firstChild === inserted)
        assertTrue(section.lastChild === existingParagraph)
        assertEquals("PrependedInserted before existing DOM children", inserted.textContent)
        assertEquals("already present", existingParagraph.textContent)
    }

    @Test
    fun attributeHelpersEncodeEnumsClassSetsAndAttributeMaps() {
        val attributes: Map<String, String> = attributesMapOf("role", "button", "aria-label", "Launch")
        val classes: LinkedHashSet<String> = linkedSetOf("btn", "btn-primary", "active")
        val decodedClasses: Set<String> = stringSetDecode("btn btn-primary active") ?: emptySet()

        assertEquals("button", attributes["role"])
        assertEquals("Launch", attributes["aria-label"])
        assertEquals("checkbox", InputType.checkBox.enumEncode())
        assertEquals("btn btn-primary active", classes.stringSetEncode())
        assertEquals(classes, decodedClasses)
    }
}
