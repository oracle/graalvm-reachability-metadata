/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_xmlunit.xmlunit_core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.ComparisonType;
import org.xmlunit.diff.DefaultNodeMatcher;
import org.xmlunit.diff.Diff;
import org.xmlunit.diff.DifferenceEvaluators;
import org.xmlunit.diff.ElementSelector;
import org.xmlunit.diff.ElementSelectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises XMLUnit's conditional element selector subsystem to reconcile fulfillment batches with reordered line items.
public class FulfillmentBatchConditionalMatchingTest {
    @Test
    @Timeout(60)
    public void matchesReorderedLineItemsBySkuButStillDetectsQuantityChanges() {
        String expectedBatch = """
                <fulfillmentBatch>
                    <order id="ORDER-42">
                        <customer>Casey Morgan</customer>
                        <line sku="A-100"><quantity>2</quantity></line>
                        <line sku="B-200"><quantity>1</quantity></line>
                    </order>
                </fulfillmentBatch>
                """;
        String receivedBatch = """
                <fulfillmentBatch>
                    <order id="ORDER-42">
                        <customer>Casey Morgan</customer>
                        <line sku="B-200"><quantity>1</quantity></line>
                        <line sku="A-100"><quantity>2</quantity></line>
                    </order>
                </fulfillmentBatch>
                """;

        Diff reorderedBatch = compareBatches(expectedBatch, receivedBatch);

        assertFalse(reorderedBatch.hasDifferences(), reorderedBatch::fullDescription);

        Diff changedBatch = compareBatches(expectedBatch, receivedBatch.replace(
                "<quantity>2</quantity>", "<quantity>3</quantity>"));

        assertTrue(changedBatch.hasDifferences());
    }

    private static Diff compareBatches(String expectedBatch, String receivedBatch) {
        ElementSelector selector = ElementSelectors.conditionalBuilder()
                .whenElementIsNamed("line")
                .thenUse(ElementSelectors.byNameAndAttributes("sku"))
                .elseUse(ElementSelectors.byName)
                .build();
        return DiffBuilder.compare(Input.fromString(expectedBatch))
                .withTest(Input.fromString(receivedBatch))
                .withNodeMatcher(new DefaultNodeMatcher(selector))
                .withDifferenceEvaluator(DifferenceEvaluators.chain(
                        DifferenceEvaluators.downgradeDifferencesToSimilar(
                                ComparisonType.CHILD_NODELIST_SEQUENCE),
                        DifferenceEvaluators.Default))
                .checkForSimilar()
                .build();
    }
}
