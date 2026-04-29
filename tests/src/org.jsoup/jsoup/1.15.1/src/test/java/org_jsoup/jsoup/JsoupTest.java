/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jsoup.jsoup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.nodes.FormElement;
import org.jsoup.nodes.Node;
import org.jsoup.parser.Parser;
import org.jsoup.safety.Safelist;
import org.jsoup.select.Elements;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;
import org.junit.jupiter.api.Test;

public class JsoupTest {
    @Test
    public void parsesHtmlDocumentAndExposesDomNavigation() {
        String html = """
                <!doctype html>
                <html>
                <head>
                    <title>Example article</title>
                    <meta charset="utf-8">
                </head>
                <body>
                    <nav><a href="../about">About</a></nav>
                    <article id="story" class="post featured" data-id="42">
                        <h1>Readable HTML</h1>
                        <p class="lead">Jsoup parses <em>real-world</em> markup.</p>
                        <p>Second paragraph with <a href="/docs/start.html?x=1#top">docs</a>.</p>
                    </article>
                </body>
                </html>
                """;

        Document document = Jsoup.parse(html, "file:/news/index.html");
        Element article = requireFirst(document, "article.post.featured[data-id=42]");
        Element lead = requireFirst(article, "p.lead");
        Element docsLink = requireFirst(article, "p + p a[href]");

        assertThat(document.title()).isEqualTo("Example article");
        assertThat(document.charset().name()).isEqualTo("UTF-8");
        assertThat(article.id()).isEqualTo("story");
        assertThat(article.classNames()).containsExactlyInAnyOrder("post", "featured");
        assertThat(article.dataset()).containsEntry("id", "42");
        assertThat(lead.ownText()).isEqualTo("Jsoup parses markup.");
        assertThat(lead.text()).isEqualTo("Jsoup parses real-world markup.");
        assertThat(docsLink.absUrl("href")).isEqualTo("file:/docs/start.html?x=1#top");
        assertThat(requireFirst(document, "nav a").absUrl("href")).isEqualTo("file:/about");
    }

    @Test
    public void selectsElementsWithCombinatorsPseudoSelectorsAndAttributeQueries() {
        Document document = Jsoup.parse("""
                <main>
                    <section class="catalog" data-region="eu-west">
                        <h2>Books</h2>
                        <ul>
                            <li data-sku="book-001" class="item selected"><span class="name">Native Java</span> <b>19.99</b></li>
                            <li data-sku="book-002" class="item"><span class="name">HTML Parsing</span> <b>24.50</b></li>
                            <li data-sku="mag-101" class="item"><span class="name">Monthly Digest</span> <b>4.99</b></li>
                        </ul>
                    </section>
                    <section class="catalog" data-region="us-east">
                        <h2>Music</h2>
                        <p class="empty">No products yet</p>
                    </section>
                </main>
                """);

        Elements catalogSections = document.select("section.catalog:has(li.item)");
        Elements bookItems = document.select("section[data-region^=eu] li[data-sku^=book-]");
        Elements matchedByTextItems = document.select("li.item:matches(HTML Parsing|Monthly Digest)");
        Elements followingItems = document.select("li.selected ~ li");

        assertThat(catalogSections).hasSize(1);
        assertThat(bookItems.eachText()).containsExactly("Native Java 19.99", "HTML Parsing 24.50");
        assertThat(document.select("li.item:nth-of-type(2) .name").text()).isEqualTo("HTML Parsing");
        assertThat(document.select("p:containsOwn(No products yet)")).hasSize(1);
        assertThat(document.select("li[data-sku$=101]").text()).isEqualTo("Monthly Digest 4.99");
        assertThat(followingItems).hasSize(2);
        assertThat(document.select("section.catalog:not([data-region=eu-west]) h2").text()).isEqualTo("Music");
        assertThat(matchedByTextItems.eachText()).containsExactly("HTML Parsing 24.50", "Monthly Digest 4.99");
    }

    @Test
    public void mutatesElementsAttributesAndGeneratedMarkup() {
        Document document = Jsoup.parseBodyFragment("<div id='root'><p class='intro'>Hello</p></div>");
        document.outputSettings().prettyPrint(false);
        Element root = requireFirst(document, "#root");
        Element intro = requireFirst(root, "p.intro");

        root.prependElement("header").text("Title");
        Element list = root.appendElement("ol").attr("data-kind", "steps");
        list.appendElement("li").text("Parse");
        list.appendElement("li").text("Select");
        list.appendElement("li").text("Manipulate");
        intro.addClass("lead").attr("data-source", "fixture").text("Hello jsoup");
        intro.before("<aside>Before paragraph</aside>");
        intro.after("<aside>After paragraph</aside>");
        intro.toggleClass("intro");

        assertThat(root.children().eachText())
                .containsExactly("Title", "Before paragraph", "Hello jsoup", "After paragraph", "Parse Select Manipulate");
        assertThat(intro.hasClass("intro")).isFalse();
        assertThat(intro.hasClass("lead")).isTrue();
        assertThat(intro.dataset()).containsEntry("source", "fixture");
        assertThat(list.children().eachText()).containsExactly("Parse", "Select", "Manipulate");
        assertThat(document.body().html()).contains("<p class=\"lead\" data-source=\"fixture\">Hello jsoup</p>");
    }

    @Test
    public void sanitizesUntrustedHtmlWithCustomWhitelist() {
        String dirtyHtml = """
                <section data-safe="yes" data-secret="no">
                    <h1>Welcome</h1>
                    <script>alert('x')</script>
                    <p onclick="steal()">Read <a href="https://example.test/read" rel="nofollow">more</a></p>
                    <a href="javascript:alert(1)">bad link</a>
                    <img src="https://example.test/logo.png" onerror="steal()" alt="logo">
                </section>
                """;
        Safelist safelist = Safelist.relaxed()
                .addTags("section")
                .addAttributes("section", "data-safe")
                .addProtocols("a", "href", "https")
                .addProtocols("img", "src", "https");

        String sanitized = Jsoup.clean(dirtyHtml, safelist);
        Document cleaned = Jsoup.parseBodyFragment(sanitized);
        Element section = requireFirst(cleaned, "section[data-safe=yes]");
        Element safeLink = requireFirst(cleaned, "a[href='https://example.test/read']");
        Element image = requireFirst(cleaned, "img[src='https://example.test/logo.png']");

        assertThat(cleaned.select("script")).isEmpty();
        assertThat(cleaned.select("[onclick], [onerror], [data-secret]")).isEmpty();
        assertThat(cleaned.select("a[href^=javascript]")).isEmpty();
        assertThat(section.text()).contains("Welcome", "Read more", "bad link");
        assertThat(safeLink.text()).isEqualTo("more");
        assertThat(image.attr("alt")).isEqualTo("logo");
    }

    @Test
    public void parsesXmlWhilePreservingCaseNamespacesAndTextNodes() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns:media="https://example.test/media">
                    <entry id="a1">
                        <Title>First &amp; Foremost</Title>
                        <media:thumbnail url="https://example.test/thumb.png" width="64" />
                    </entry>
                    <entry id="a2"><Title>Second</Title></entry>
                </feed>
                """;

        Document document = Jsoup.parse(xml, "", Parser.xmlParser());
        document.outputSettings().syntax(Document.OutputSettings.Syntax.xml);
        Element firstEntry = requireFirst(document, "entry[id=a1]");
        Element thumbnail = document.getElementsByTag("media:thumbnail").first();
        List<String> rootChildNodeNames = document.childNodes().stream()
                .map(Node::nodeName)
                .collect(Collectors.toList());

        assertThat(document.select("Title").eachText()).containsExactly("First & Foremost", "Second");
        assertThat(firstEntry.children().first().tagName()).isEqualTo("Title");
        assertThat(thumbnail).isNotNull();
        assertThat(thumbnail.attr("url")).isEqualTo("https://example.test/thumb.png");
        assertThat(thumbnail.attr("width")).isEqualTo("64");
        assertThat(rootChildNodeNames).contains("#declaration", "feed");
    }

    @Test
    public void extractsSubmittedFormDataFromControls() {
        Document document = Jsoup.parse("""
                <form id="search" action="/submit" method="post">
                    <input type="text" name="q" value="graalvm">
                    <input type="password" name="ignored" value="secret" disabled>
                    <input type="checkbox" name="feature" value="metadata" checked>
                    <input type="checkbox" name="feature" value="agent">
                    <input type="radio" name="mode" value="fast">
                    <input type="radio" name="mode" value="safe" checked>
                    <select name="format">
                        <option value="html">HTML</option>
                        <option value="xml" selected>XML</option>
                    </select>
                    <textarea name="notes">Use public APIs</textarea>
                    <button type="button" value="go">Go</button>
                </form>
                """, "https://example.test/app/index.html");
        FormElement form = (FormElement) requireFirst(document, "form#search");
        requireFirst(form, "input[name=q]").val("native image");
        requireFirst(form, "option[value=xml]").removeAttr("selected");
        requireFirst(form, "option[value=html]").attr("selected", "selected");

        List<Connection.KeyVal> formData = form.formData();
        Map<String, List<String>> valuesByKey = formData.stream()
                .collect(Collectors.groupingBy(
                        Connection.KeyVal::key,
                        LinkedHashMap::new,
                        Collectors.mapping(Connection.KeyVal::value, Collectors.toList())));

        assertThat(form.attr("action")).isEqualTo("/submit");
        assertThat(form.attr("method")).isEqualTo("post");
        assertThat(valuesByKey).containsEntry("q", List.of("native image"));
        assertThat(valuesByKey).containsEntry("feature", List.of("metadata"));
        assertThat(valuesByKey).containsEntry("mode", List.of("safe"));
        assertThat(valuesByKey).containsEntry("format", List.of("html"));
        assertThat(valuesByKey).containsEntry("notes", List.of("Use public APIs"));
        assertThat(valuesByKey).doesNotContainKey("ignored");
        assertThat(valuesByKey).doesNotContainKey("submit");
    }

    @Test
    public void controlsOutputEscapingCharsetAndSyntax() {
        Document document = Jsoup.parseBodyFragment("<p title='snowman \u2603 & copy \u00a9'>5 < 7 & \u03c0</p><br>");
        Document.OutputSettings outputSettings = new Document.OutputSettings()
                .charset("US-ASCII")
                .escapeMode(Entities.EscapeMode.xhtml)
                .syntax(Document.OutputSettings.Syntax.xml)
                .prettyPrint(false);
        document.outputSettings(outputSettings);

        String escapedText = Entities.escape("\u00a9 \u03c0 \u2603 & <", document.outputSettings());
        Document.OutputSettings clonedSettings = document.outputSettings().clone()
                .charset("UTF-8")
                .escapeMode(Entities.EscapeMode.extended);

        assertThat(document.body().html())
                .isEqualTo("<p title=\"snowman &#x2603; &amp; copy &#xa9;\">5 &lt; 7 &amp; &#x3c0;</p><br />");
        assertThat(escapedText).isEqualTo("&#xa9; &#x3c0; &#x2603; &amp; &lt;");
        assertThat(document.outputSettings().charset().name()).isEqualTo("US-ASCII");
        assertThat(document.outputSettings().escapeMode()).isEqualTo(Entities.EscapeMode.xhtml);
        assertThat(document.outputSettings().syntax()).isEqualTo(Document.OutputSettings.Syntax.xml);
        assertThat(clonedSettings.charset().name()).isEqualTo("UTF-8");
        assertThat(clonedSettings.escapeMode()).isEqualTo(Entities.EscapeMode.extended);
    }

    @Test
    public void iteratesAttributesAndKeepsTextAndHtmlEscapingDistinct() {
        Document document = Jsoup.parseBodyFragment("<a id='download' href='/file?a=1&amp;b=2' title='A &amp; B'>A &amp; B</a>");
        Element link = requireFirst(document, "a#download");
        Map<String, String> attributes = new LinkedHashMap<>();
        for (Attribute attribute : link.attributes()) {
            attributes.put(attribute.getKey(), attribute.getValue());
        }

        assertThat(attributes).containsEntry("href", "/file?a=1&b=2");
        assertThat(attributes).containsEntry("title", "A & B");
        assertThat(link.text()).isEqualTo("A & B");
        assertThat(link.html()).isEqualTo("A &amp; B");
        assertThat(link.outerHtml()).contains("href=\"/file?a=1&amp;b=2\"");
    }

    @Test
    public void traversesNodeTreeWithDepthAwareVisitorCallbacks() {
        Document document = Jsoup.parseBodyFragment("<article><h1>Title</h1><p>Lead <em>details</em></p><!-- editorial note --></article>");
        Element article = requireFirst(document, "article");
        List<String> events = new ArrayList<>();

        NodeTraversor.traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
                events.add("enter:" + depth + ":" + node.nodeName());
            }

            @Override
            public void tail(Node node, int depth) {
                if (node instanceof Element) {
                    events.add("exit:" + depth + ":" + node.nodeName());
                }
            }
        }, article);

        assertThat(events).containsExactly(
                "enter:0:article",
                "enter:1:h1",
                "enter:2:#text",
                "exit:1:h1",
                "enter:1:p",
                "enter:2:#text",
                "enter:2:em",
                "enter:3:#text",
                "exit:2:em",
                "exit:1:p",
                "enter:1:#comment",
                "exit:0:article");
    }

    private static Element requireFirst(Element root, String cssQuery) {
        Element element = root.selectFirst(cssQuery);
        assertThat(element).as("Expected selector <%s> under <%s> to match", cssQuery, root.nodeName()).isNotNull();
        return element;
    }
}
