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
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.s9api.XQueryCompiler;
import net.sf.saxon.s9api.XQueryExecutable;
import net.sf.saxon.s9api.XQueryEvaluator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises Saxon's XQuery compilation and evaluation subsystem to calculate customer order totals.
 *
 * An order service compiles a parameterized query once and applies it to an XML order feed for
 * each customer. The service returns orders in descending total order, which is suitable for a
 * customer summary response.
 */
public class OrderTotalsXQueryTest {

    private static final String ORDERS = """
            <orders>
              <order id="A-17" customer="acme">
                <line sku="PAPER" quantity="4" unitPrice="3.25"/>
                <line sku="PENS" quantity="10" unitPrice="1.50"/>
              </order>
              <order id="A-18" customer="acme">
                <line sku="CHAIR" quantity="2" unitPrice="45.00"/>
              </order>
              <order id="B-04" customer="bravo">
                <line sku="MUG" quantity="3" unitPrice="8.00"/>
              </order>
            </orders>
            """;

    private static final String ORDER_TOTALS_QUERY = """
            declare variable $customer external;
            for $order in /orders/order[@customer = $customer]
            let $total := sum(
              for $line in $order/line
              return xs:decimal($line/@quantity) * xs:decimal($line/@unitPrice)
            )
            order by $total descending
            return concat(string($order/@id), ":", format-number($total, "0.00"))
            """;

    @Test
    @Timeout(60)
    public void returnsCustomerOrdersByDescendingTotal() throws Exception {
        Processor processor = new Processor(false);
        DocumentBuilder documentBuilder = processor.newDocumentBuilder();
        XdmNode orders = documentBuilder.build(new StreamSource(new StringReader(ORDERS)));

        XQueryCompiler compiler = processor.newXQueryCompiler();
        XQueryExecutable orderTotals = compiler.compile(ORDER_TOTALS_QUERY);
        QName customer = new QName("customer");

        assertEquals(List.of("A-18:90.00", "A-17:28.00"),
                findOrderTotals(orderTotals, orders, customer, "acme"));
        assertEquals(List.of("B-04:24.00"),
                findOrderTotals(orderTotals, orders, customer, "bravo"));
        assertEquals(List.of(), findOrderTotals(orderTotals, orders, customer, "charlie"));
    }

    private static List<String> findOrderTotals(
            XQueryExecutable orderTotals,
            XdmNode orders,
            QName customer,
            String customerValue) throws Exception {
        XQueryEvaluator evaluator = orderTotals.load();
        evaluator.setContextItem(orders);
        evaluator.setExternalVariable(customer, new XdmAtomicValue(customerValue));
        XdmValue result = evaluator.evaluate();

        List<String> totals = new ArrayList<>();
        for (XdmItem item : result) {
            totals.add(item.getStringValue());
        }
        return totals;
    }
}
