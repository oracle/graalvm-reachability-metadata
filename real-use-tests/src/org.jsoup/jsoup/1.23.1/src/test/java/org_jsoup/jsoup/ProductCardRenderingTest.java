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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Jsoup's DOM editing subsystem to render a product card for a catalog application.
 */
public class ProductCardRenderingTest {

    @Test
    @Timeout(60)
    public void buildsTheProductCardFromCatalogData() {
        Document page = Jsoup.parse("""
                <!doctype html>
                <html>
                <body>
                <main id="catalog">
                  <article class="product" data-sku="mug-7">
                    <h2>Trail Mug</h2>
                    <p class="price">$18.50</p>
                  </article>
                </main>
                </body>
                </html>
                """);

        Element product = page.selectFirst("article.product");
        product.attr("data-state", "available");
        product.selectFirst("h2").text("Trail Mug - New");
        product.prependElement("a")
                .addClass("product-link")
                .attr("href", "/products/mug-7")
                .text("View details");
        product.appendElement("span")
                .addClass("badge")
                .text("In stock");

        Element link = product.selectFirst("a.product-link");
        String renderedCard = product.outerHtml();

        assertEquals("available", product.attr("data-state"));
        assertEquals("/products/mug-7", link.attr("href"));
        assertEquals("Trail Mug - New", product.selectFirst("h2").text());
        assertEquals("In stock", product.selectFirst("span.badge").text());
        assertTrue(renderedCard.contains("data-sku=\"mug-7\""));
        assertTrue(renderedCard.contains("class=\"product-link\""));
    }
}
