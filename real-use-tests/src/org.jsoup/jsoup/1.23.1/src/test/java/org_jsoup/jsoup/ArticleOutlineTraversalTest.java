/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jsoup.jsoup;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises Jsoup's DOM traversal subsystem to build an article outline for a publishing application.
 */
public class ArticleOutlineTraversalTest {

    @Test
    @Timeout(60)
    public void buildsOutlineAndLinkIndexWhileTraversingArticle() {
        Document articlePage = Jsoup.parse("""
                <!doctype html>
                <html>
                <body>
                <article data-id="trail-42">
                  <h1>Rain on the ridge</h1>
                  <p>The route is open after the storm.</p>
                  <h2>What to pack</h2>
                  <p>Bring a <a href="/gear/jacket">dry jacket</a>.</p>
                  <h2>Route notes</h2>
                  <p>See the <a href="/maps/ridge-42">ridge map</a> before leaving.</p>
                </article>
                </body>
                </html>
                """, "https://journal.example.test/posts/42");
        Element article = articlePage.selectFirst("article[data-id=trail-42]");
        List<OutlineEntry> outline = new ArrayList<>();
        List<String> links = new ArrayList<>();

        NodeTraversor.traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
                if (!(node instanceof Element element)) {
                    return;
                }
                if (element.tagName().matches("h[1-6]")) {
                    outline.add(new OutlineEntry(
                            Integer.parseInt(element.tagName().substring(1)), element.text()));
                }
                if (element.is("a")) {
                    links.add(element.absUrl("href"));
                }
            }
        }, article);

        assertEquals(List.of(
                new OutlineEntry(1, "Rain on the ridge"),
                new OutlineEntry(2, "What to pack"),
                new OutlineEntry(2, "Route notes")), outline);
        assertEquals(List.of(
                "https://journal.example.test/gear/jacket",
                "https://journal.example.test/maps/ridge-42"), links);
    }

    private record OutlineEntry(int level, String title) {
    }
}
