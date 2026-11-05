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
import org.jsoup.select.Elements;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Jsoup's CSS selector subsystem to find purchasable products in a catalog page.
 */
public class ProductCatalogSelectionTest {

    @Test
    @Timeout(60)
    public void selectsPurchasableProductsAndTheirDetails() {
        String catalogPage = """
                <!doctype html>
                <html>
                <body>
                <main id="catalog">
                  <article class="product" data-sku="mug-7" data-stock="in-stock">
                    <h2>Trail Mug</h2>
                    <p class="price">$18.50</p>
                    <button class="add-to-cart">Add to cart</button>
                  </article>
                  <article class="product" data-sku="pack-3" data-stock="sold-out">
                    <h2>Day Pack</h2>
                    <p class="price">$64.00</p>
                    <button class="notify">Notify me</button>
                  </article>
                  <article class="product featured" data-sku="lamp-2" data-stock="in-stock">
                    <h2>Camp Lamp</h2>
                    <p class="price">$29.00</p>
                    <button class="add-to-cart">Add to cart</button>
                  </article>
                </main>
                </body>
                </html>
                """;

        Document catalog = Jsoup.parse(catalogPage);
        Elements purchasable = catalog.select(
                "#catalog article.product[data-stock=in-stock]:has(button.add-to-cart)");
        List<String> productNames = purchasable.stream()
                .map(product -> product.select("h2").text())
                .collect(Collectors.toList());
        Element featured = catalog.select("article.product.featured").first();

        assertEquals(List.of("Trail Mug", "Camp Lamp"), productNames);
        assertEquals("mug-7", purchasable.get(0).attr("data-sku"));
        assertEquals("$29.00", featured.select(".price").text());
        assertTrue(catalog.select("article[data-stock=sold-out]").text().contains("Day Pack"));
    }
}
