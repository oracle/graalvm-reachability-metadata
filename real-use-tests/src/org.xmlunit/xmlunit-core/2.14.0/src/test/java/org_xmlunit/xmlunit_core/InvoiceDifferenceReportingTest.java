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
import org.xmlunit.diff.Comparison;
import org.xmlunit.diff.ComparisonFormatter;
import org.xmlunit.diff.ComparisonType;
import org.xmlunit.diff.Diff;
import org.xmlunit.diff.Difference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises XMLUnit's comparison formatting subsystem to turn invoice mismatches into an application-facing audit message.
public class InvoiceDifferenceReportingTest {
    @Test
    @Timeout(60)
    public void reportsChangedInvoiceFieldsWithBusinessPathsAndValues() {
        ComparisonFormatter formatter = new InvoiceAuditFormatter();
        Diff invoiceDiff = DiffBuilder.compare(Input.fromString("""
                <invoice>
                    <invoiceNumber>INV-2048</invoiceNumber>
                    <customerId>customer-42</customerId>
                    <total currency="USD">69.98</total>
                </invoice>
                """))
                .withTest(Input.fromString("""
                        <invoice>
                            <invoiceNumber>INV-2048</invoiceNumber>
                            <customerId>customer-42</customerId>
                            <total currency="USD">79.98</total>
                        </invoice>
                        """))
                .withComparisonFormatter(formatter)
                .checkForIdentical()
                .build();

        assertTrue(invoiceDiff.hasDifferences());
        List<String> auditEntries = new ArrayList<>();
        for (Difference difference : invoiceDiff.getDifferences()) {
            auditEntries.add(difference.toString(formatter));
        }

        assertEquals(List.of("TEXT_VALUE /invoice[1]/total[1]/text()[1]: expected=69.98, actual=79.98 (DIFFERENT)"),
                auditEntries);
        assertEquals("TEXT_VALUE /invoice[1]/total[1]/text()[1]: expected=69.98, actual=79.98",
                invoiceDiff.fullDescription());
        assertFalse(invoiceDiff.toString(formatter).isEmpty());
    }

    private static final class InvoiceAuditFormatter implements ComparisonFormatter {
        @Override
        public String getDescription(Comparison comparison) {
            return comparison.getType().name() + " "
                    + comparison.getTestDetails().getXPath() + ": expected="
                    + comparison.getControlDetails().getValue() + ", actual="
                    + comparison.getTestDetails().getValue();
        }

        @Override
        public String getDetails(Comparison.Detail detail, ComparisonType type,
                                 boolean isControl) {
            return (isControl ? "expected=" : "actual=") + detail.getValue();
        }
    }
}
