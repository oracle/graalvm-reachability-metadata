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
import org.xmlunit.diff.DifferenceEvaluators;
import org.xmlunit.diff.Diff;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises XMLUnit's prolog difference evaluator subsystem to compare partner invoices across transport encodings.
public class InvoiceTransportDifferenceEvaluatorTest {
    @Test
    @Timeout(60)
    public void ignoresTransportPrologButDetectsInvoiceAmountChanges() {
        String expectedInvoice = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <invoice>
                    <invoiceNumber>INV-2048</invoiceNumber>
                    <amount currency="USD">69.98</amount>
                </invoice>
                """;
        String receivedInvoice = """
                <?xml version="1.0" encoding="ISO-8859-1" standalone="no"?>
                <invoice>
                    <invoiceNumber>INV-2048</invoiceNumber>
                    <amount currency="USD">69.98</amount>
                </invoice>
                """;

        Diff transportOnlyDifference = compareInvoices(expectedInvoice, receivedInvoice);

        assertFalse(transportOnlyDifference.hasDifferences(),
                transportOnlyDifference::fullDescription);

        Diff changedInvoice = compareInvoices(expectedInvoice, receivedInvoice.replace(
                "<amount currency=\"USD\">69.98</amount>",
                "<amount currency=\"USD\">79.98</amount>"));

        assertTrue(changedInvoice.hasDifferences());
    }

    private static Diff compareInvoices(String expectedInvoice, String receivedInvoice) {
        return DiffBuilder.compare(Input.fromString(expectedInvoice))
                .withTest(Input.fromString(receivedInvoice))
                .withDifferenceEvaluator(DifferenceEvaluators.ignorePrologDifferences())
                .checkForIdentical()
                .build();
    }
}
