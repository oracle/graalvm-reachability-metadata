/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_saxon.Saxon_HE;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.s9api.XQueryCompiler;
import net.sf.saxon.s9api.XQueryExecutable;
import net.sf.saxon.s9api.XQueryEvaluator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises Saxon's collation subsystem to sort customer-facing product labels.
 *
 * A catalog service registers the application's case-insensitive label ordering with Saxon, then
 * uses a parameterized XQuery to produce the display order for a search response.
 */
public class ProductLabelCollationTest {

    private static final String LABEL_COLLATION = "urn:catalog:product-label";

    private static final String CATALOG = """
            <catalog>
              <product sku="MUG-9" label="ceramic mug"/>
              <product sku="NOTEBOOK-42" label="Notebook"/>
              <product sku="PEN-7" label="ballpoint pen"/>
              <product sku="CABLE-3" label="Cable ties"/>
            </catalog>
            """;

    private static final String SORT_QUERY = """
            for $product in /catalog/product
            order by string($product/@label) collation "urn:catalog:product-label"
            return concat(string($product/@label), "|", string($product/@sku))
            """;

    @Test
    @Timeout(60)
    public void sortsCatalogLabelsWithApplicationCollation() throws Exception {
        Processor processor = new Processor(false);
        processor.declareCollation(LABEL_COLLATION, labelComparator());

        XQueryCompiler compiler = processor.newXQueryCompiler();
        XQueryExecutable sortQuery = compiler.compile(SORT_QUERY);
        XQueryEvaluator evaluator = sortQuery.load();
        evaluator.setContextItem(processor.newDocumentBuilder().build(
                new StreamSource(new StringReader(CATALOG))));
        XdmValue result = evaluator.evaluate();

        List<String> sortedProducts = new ArrayList<>();
        for (XdmItem item : result) {
            sortedProducts.add(item.getStringValue());
        }

        assertEquals(List.of(
                "ballpoint pen|PEN-7",
                "Cable ties|CABLE-3",
                "ceramic mug|MUG-9",
                "Notebook|NOTEBOOK-42"), sortedProducts);
    }

    private static Comparator<String> labelComparator() {
        return (left, right) -> {
            int caseInsensitiveOrder = left.toLowerCase(Locale.ROOT)
                    .compareTo(right.toLowerCase(Locale.ROOT));
            return caseInsensitiveOrder != 0
                    ? caseInsensitiveOrder : left.compareTo(right);
        };
    }
}
