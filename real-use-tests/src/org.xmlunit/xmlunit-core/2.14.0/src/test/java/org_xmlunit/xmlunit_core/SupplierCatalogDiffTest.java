/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_xmlunit.xmlunit_core;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.ComparisonResult;
import org.xmlunit.diff.ComparisonType;
import org.xmlunit.diff.DefaultNodeMatcher;
import org.xmlunit.diff.DifferenceEvaluators;
import org.xmlunit.diff.DOMDifferenceEngine;
import org.xmlunit.diff.ElementSelectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises XMLUnit's structural diff subsystem to reconcile supplier catalog updates while ignoring item order.
public class SupplierCatalogDiffTest {
    @Test
    @Timeout(60)
    public void identifiesCatalogChangesButIgnoresSupplierItemOrder() {
        List<ComparisonType> reorderedCatalogChanges = compareCatalogs("""
                <catalog>
                    <item sku="A-100"><name>Keyboard</name><price>49.99</price></item>
                    <item sku="B-200"><name>Mouse</name><price>19.99</price></item>
                </catalog>
                """, """
                <catalog>
                    <item sku="B-200"><name>Mouse</name><price>19.99</price></item>
                    <item sku="A-100"><name>Keyboard</name><price>49.99</price></item>
                </catalog>
                """);
        List<ComparisonType> changedCatalogChanges = compareCatalogs("""
                <catalog>
                    <item sku="A-100"><name>Keyboard</name><price>49.99</price></item>
                    <item sku="B-200"><name>Mouse</name><price>19.99</price></item>
                </catalog>
                """, """
                <catalog>
                    <item sku="B-200"><name>Mouse</name><price>24.99</price></item>
                    <item sku="A-100"><name>Keyboard</name><price>49.99</price></item>
                </catalog>
                """);

        assertTrue(reorderedCatalogChanges.isEmpty(),
                () -> "Reordering catalog items should not be a business change: "
                        + reorderedCatalogChanges);
        assertEquals(List.of(ComparisonType.TEXT_VALUE), changedCatalogChanges);
    }

    private static List<ComparisonType> compareCatalogs(String expectedCatalog, String supplierCatalog) {
        DOMDifferenceEngine differenceEngine = new DOMDifferenceEngine();
        differenceEngine.setNodeMatcher(new DefaultNodeMatcher(ElementSelectors.byNameAndAttributes("sku")));
        differenceEngine.setDifferenceEvaluator(DifferenceEvaluators.chain(
                DifferenceEvaluators.downgradeDifferencesToSimilar(
                        ComparisonType.CHILD_NODELIST_SEQUENCE),
                DifferenceEvaluators.Default));
        List<ComparisonType> differences = new ArrayList<>();
        differenceEngine.addDifferenceListener((comparison, result) -> {
            if (result == ComparisonResult.DIFFERENT) {
                differences.add(comparison.getType());
            }
        });
        differenceEngine.compare(Input.fromString(expectedCatalog).build(),
                Input.fromString(supplierCatalog).build());
        return differences;
    }
}
