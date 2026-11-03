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
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.ComparisonType;
import org.xmlunit.diff.ComparisonControllers;
import org.xmlunit.diff.Diff;
import org.xmlunit.diff.Difference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises XMLUnit's comparison controller subsystem to stop an order gate at its first actionable mismatch.
public class OrderGateShortCircuitTest {
    @Test
    @Timeout(60)
    public void reportsOnlyTheFirstMismatchWhenAnOrderCannotBeReleased() {
        Diff orderGate = DiffBuilder.compare(Input.fromString("""
                <order>
                    <orderId>ORDER-42</orderId>
                    <status>READY</status>
                    <total>69.98</total>
                    <currency>USD</currency>
                </order>
                """))
                .withTest(Input.fromString("""
                        <order>
                            <orderId>ORDER-42</orderId>
                            <status>HELD</status>
                            <total>79.98</total>
                            <currency>USD</currency>
                        </order>
                        """))
                .withComparisonController(ComparisonControllers.StopWhenDifferent)
                .checkForIdentical()
                .build();

        List<Difference> differences = differences(orderGate);

        assertTrue(orderGate.hasDifferences());
        assertEquals(1, differences.size());
        assertEquals(ComparisonType.TEXT_VALUE, differences.get(0).getComparison().getType());
        assertEquals("/order[1]/status[1]/text()[1]",
                differences.get(0).getComparison().getTestDetails().getXPath());
        assertEquals("HELD", differences.get(0).getComparison().getTestDetails().getValue());
    }

    private static List<Difference> differences(Diff diff) {
        List<Difference> differences = new ArrayList<>();
        for (Difference difference : diff.getDifferences()) {
            differences.add(difference);
        }
        return differences;
    }
}
