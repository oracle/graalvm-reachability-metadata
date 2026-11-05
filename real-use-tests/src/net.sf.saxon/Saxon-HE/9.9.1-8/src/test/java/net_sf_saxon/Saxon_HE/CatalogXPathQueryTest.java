/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_saxon.Saxon_HE;

import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathExecutable;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises Saxon's XPath query subsystem to select purchasable products from an XML catalog.
 *
 * A catalog service compiles a parameterized XPath once and applies it to the catalog for each
 * product category. The service returns the matching SKU attributes in catalog order, applying
 * both the customer's price limit and the available-stock rule.
 */
public class CatalogXPathQueryTest {

    private static final String CATALOG = """
            <catalog xmlns="urn:catalog">
              <product sku="NOTEBOOK-42" category="office" price="12.50" stock="18"/>
              <product sku="PEN-7" category="office" price="4.25" stock="0"/>
              <product sku="MONITOR-3" category="office" price="249.00" stock="3"/>
              <product sku="MUG-9" category="kitchen" price="15.00" stock="7"/>
              <product sku="KETTLE-2" category="kitchen" price="39.00" stock="2"/>
            </catalog>
            """;

    @Test
    @Timeout(60)
    public void findsInStockProductsWithinCategoryBudget() throws Exception {
        Processor processor = new Processor(false);
        DocumentBuilder documentBuilder = processor.newDocumentBuilder();
        XdmNode catalog = documentBuilder.build(
                new StreamSource(new StringReader(CATALOG)));

        XPathCompiler compiler = processor.newXPathCompiler();
        compiler.declareNamespace("c", "urn:catalog");
        QName category = new QName("category");
        QName maxPrice = new QName("maxPrice");
        compiler.declareVariable(category);
        compiler.declareVariable(maxPrice);
        XPathExecutable productQuery = compiler.compile(
                "/c:catalog/c:product["
                        + "@category = $category"
                        + " and number(@price) <= $maxPrice"
                        + " and number(@stock) > 0]/@sku");

        assertEquals(List.of("NOTEBOOK-42"),
                findSkus(productQuery, catalog, category, "office", maxPrice, 25));
        assertEquals(List.of("MUG-9"),
                findSkus(productQuery, catalog, category, "kitchen", maxPrice, 20));
    }

    private static List<String> findSkus(
            XPathExecutable productQuery,
            XdmNode catalog,
            QName category,
            String categoryValue,
            QName maxPrice,
            double maxPriceValue) throws Exception {
        XPathSelector selector = productQuery.load();
        selector.setContextItem(catalog);
        selector.setVariable(category, new XdmAtomicValue(categoryValue));
        selector.setVariable(maxPrice, new XdmAtomicValue(maxPriceValue));
        XdmValue selectedSkus = selector.evaluate();

        List<String> skus = new ArrayList<>();
        for (XdmItem selectedSku : selectedSkus) {
            skus.add(selectedSku.getStringValue());
        }
        return skus;
    }
}
