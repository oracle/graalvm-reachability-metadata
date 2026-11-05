/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jsoup.jsoup;

import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.w3c.dom.NodeList;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Jsoup's W3C DOM conversion subsystem to query an article for a publishing application.
 */
public class W3cDomConversionTest {

    @Test
    @Timeout(60)
    public void convertsArticleForXmlQueriesAndRetainsSourceNodes() {
        String articleMarkup = """
                <!doctype html>
                <html>
                <body>
                <article data-id="trail-42">
                  <h1>Rain on the ridge</h1>
                  <p class="summary">A short route update.</p>
                  <p class="summary">Bring a dry jacket.</p>
                </article>
                </body>
                </html>
                """;
        Document article = Jsoup.parse(articleMarkup, "https://journal.example.test/posts/42");
        W3CDom bridge = new W3CDom().namespaceAware(false);

        org.w3c.dom.Document w3cArticle = bridge.fromJsoup(article);
        NodeList summaryNodes = bridge.selectXpath(
                "//article[@data-id='trail-42']/p[@class='summary']", w3cArticle);
        List<Element> sourceSummaries = bridge.sourceNodes(summaryNodes, Element.class);

        assertEquals(2, summaryNodes.getLength());
        assertEquals("A short route update.", summaryNodes.item(0).getTextContent().trim());
        assertEquals(List.of("A short route update.", "Bring a dry jacket."),
                sourceSummaries.stream().map(Element::text).toList());
        assertEquals("trail-42", sourceSummaries.get(0).parent().attr("data-id"));
        assertTrue(bridge.asString(w3cArticle).contains("Rain on the ridge"));
    }
}
