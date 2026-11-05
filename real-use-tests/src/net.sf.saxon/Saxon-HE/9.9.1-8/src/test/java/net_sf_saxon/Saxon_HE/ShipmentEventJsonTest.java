/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_saxon.Saxon_HE;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.s9api.XQueryCompiler;
import net.sf.saxon.s9api.XQueryExecutable;
import net.sf.saxon.s9api.XQueryEvaluator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises Saxon's JSON parsing and XDM map-navigation subsystem for shipment events.
 *
 * A fulfillment service receives a JSON order event, parses it with Saxon's XQuery support, and
 * returns the order summary that an application can send to its dispatch queue.
 */
public class ShipmentEventJsonTest {

    private static final String SHIPMENT_EVENT = """
            {
              "orderId": "ORD-2048",
              "customer": {"name": "acme"},
              "items": [
                {"sku": "NOTEBOOK-42", "quantity": 2},
                {"sku": "MUG-9", "quantity": 1}
              ]
            }
            """;

    private static final String SUMMARY_QUERY = """
            declare variable $payload external;
            let $order := parse-json($payload)
            return (
              string($order?orderId),
              string($order?customer?name),
              for $item in $order?items?*
              return concat(string($item?sku), ":", string($item?quantity))
            )
            """;

    @Test
    @Timeout(60)
    public void parsesShipmentEventAndBuildsDispatchSummary() throws Exception {
        Processor processor = new Processor(false);
        XQueryCompiler compiler = processor.newXQueryCompiler();
        XQueryExecutable summaryQuery = compiler.compile(SUMMARY_QUERY);
        QName payload = new QName("payload");

        XQueryEvaluator evaluator = summaryQuery.load();
        evaluator.setExternalVariable(payload, new XdmAtomicValue(SHIPMENT_EVENT));
        XdmValue result = evaluator.evaluate();

        List<String> summary = new ArrayList<>();
        for (XdmItem item : result) {
            summary.add(item.getStringValue());
        }

        assertEquals(List.of(
                "ORD-2048",
                "acme",
                "NOTEBOOK-42:2",
                "MUG-9:1"), summary);
    }
}
