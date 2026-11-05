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
import org.jsoup.select.NodeFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Jsoup's DOM filtering subsystem to remove recommendation and tracking fragments from an article.
 */
public class ArticleContentFilteringTest {

    @Test
    @Timeout(60)
    public void removesNonEditorialFragmentsBeforePublishingArticle() {
        Document articlePage = Jsoup.parse("""
                <!doctype html>
                <html>
                <body>
                <article data-id="ridge-42">
                  <h1>Rain on the ridge</h1>
                  <p class="summary">The route is open after the storm.</p>
                  <aside class="recommendations">
                    <a href="/sponsored/boots">Featured hiking boots</a>
                  </aside>
                  <p>Bring a dry jacket and check the ridge map.</p>
                  <div class="tracking-pixel"><img src="/pixel.gif" alt=""></div>
                  <footer><a href="/legal">Reader information</a></footer>
                </article>
                </body>
                </html>
                """, "https://journal.example.test/posts/42");
        Element article = articlePage.selectFirst("article[data-id=ridge-42]");

        article.filter(new NodeFilter() {
            @Override
            public FilterResult head(Node node, int depth) {
                if (!(node instanceof Element element)) {
                    return FilterResult.CONTINUE;
                }
                if (element.is("aside.recommendations, .tracking-pixel")) {
                    return FilterResult.REMOVE;
                }
                return FilterResult.CONTINUE;
            }
        });

        assertEquals("Rain on the ridge", article.selectFirst("h1").text());
        assertEquals(2, article.select("p").size());
        assertEquals(1, article.select("footer a").size());
        assertTrue(article.text().contains("The route is open after the storm."));
        assertTrue(article.text().contains("Bring a dry jacket and check the ridge map."));
        assertFalse(article.html().contains("Featured hiking boots"));
        assertFalse(article.html().contains("pixel.gif"));
    }
}
