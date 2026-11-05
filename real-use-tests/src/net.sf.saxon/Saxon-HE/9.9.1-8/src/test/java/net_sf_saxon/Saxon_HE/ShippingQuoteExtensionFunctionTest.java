/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_saxon.Saxon_HE;

import net.sf.saxon.s9api.ExtensionFunction;
import net.sf.saxon.s9api.ItemType;
import net.sf.saxon.s9api.OccurrenceIndicator;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SequenceType;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.s9api.XQueryCompiler;
import net.sf.saxon.s9api.XQueryExecutable;
import net.sf.saxon.s9api.XQueryEvaluator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises Saxon's extension-function subsystem to calculate shipping quotes in an order feed.
 *
 * An order service registers its carrier's rate function with Saxon, then uses a parameterized
 * XQuery to combine that application function with each order's subtotal. The returned quote
 * strings can be sent directly to the checkout response.
 */
public class ShippingQuoteExtensionFunctionTest {

    private static final String ORDERS = """
            <orders>
              <order id="A-17" region="north" subtotal="82.50"/>
              <order id="A-18" region="south" subtotal="125.00"/>
              <order id="B-04" region="international" subtotal="40.00"/>
            </orders>
            """;

    private static final String QUOTE_QUERY = """
            declare namespace ship = "urn:checkout:shipping";
            for $order in /orders/order
            let $subtotal := xs:decimal($order/@subtotal)
            let $fee := ship:fee(string($order/@region), $subtotal)
            return concat(string($order/@id), ":", format-number($subtotal + $fee, "0.00"))
            """;

    @Test
    @Timeout(60)
    public void calculatesCheckoutTotalsWithRegisteredShippingFunction() throws Exception {
        Processor processor = new Processor(false);
        processor.registerExtensionFunction(new ShippingFeeFunction());

        XQueryCompiler compiler = processor.newXQueryCompiler();
        XQueryExecutable quoteQuery = compiler.compile(QUOTE_QUERY);
        XdmValue orders = processor.newDocumentBuilder().build(
                new StreamSource(new StringReader(ORDERS)));

        XQueryEvaluator evaluator = quoteQuery.load();
        evaluator.setContextItem(orders.itemAt(0));
        XdmValue result = evaluator.evaluate();

        List<String> quotes = new ArrayList<>();
        for (XdmItem item : result) {
            quotes.add(item.getStringValue());
        }

        assertEquals(List.of("A-17:87.00", "A-18:125.00", "B-04:55.00"), quotes);
    }

    private static final class ShippingFeeFunction implements ExtensionFunction {

        private static final QName NAME = new QName("ship", "urn:checkout:shipping", "fee");
        private static final SequenceType STRING = SequenceType.makeSequenceType(
                ItemType.STRING, OccurrenceIndicator.ONE);
        private static final SequenceType DECIMAL = SequenceType.makeSequenceType(
                ItemType.DECIMAL, OccurrenceIndicator.ONE);

        @Override
        public QName getName() {
            return NAME;
        }

        @Override
        public SequenceType getResultType() {
            return DECIMAL;
        }

        @Override
        public SequenceType[] getArgumentTypes() {
            return new SequenceType[]{STRING, DECIMAL};
        }

        @Override
        public XdmValue call(XdmValue[] arguments) {
            String region = arguments[0].itemAt(0).getStringValue();
            BigDecimal subtotal = ((XdmAtomicValue) arguments[1].itemAt(0)).getValue() instanceof BigDecimal
                    ? (BigDecimal) ((XdmAtomicValue) arguments[1].itemAt(0)).getValue()
                    : new BigDecimal(arguments[1].itemAt(0).getStringValue());
            BigDecimal fee = switch (region) {
                case "north" -> new BigDecimal("4.50");
                case "south" -> subtotal.compareTo(new BigDecimal("100.00")) >= 0
                        ? BigDecimal.ZERO : new BigDecimal("7.25");
                case "international" -> new BigDecimal("15.00");
                default -> throw new IllegalArgumentException("Unknown shipping region: " + region);
            };
            return new XdmAtomicValue(fee);
        }
    }
}
