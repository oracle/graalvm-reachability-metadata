/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_dom4j.dom4j;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.XPath;
import org.dom4j.rule.Action;
import org.dom4j.rule.Rule;
import org.dom4j.rule.Stylesheet;
import org.dom4j.rule.pattern.DefaultPattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/// Exercises dom4j's rule-processing subsystem to route orders in an application workflow.
public class RuleOrderRouterTest {
    @Test
    @Timeout(60)
    public void routesOrdersWithMatchingRules() throws Exception {
        String ordersXml = """
                <orders>
                  <order id="ORD-17" priority="urgent">
                    <customer>Acme Bakery</customer>
                    <total>42.50</total>
                  </order>
                  <order id="ORD-18" priority="standard">
                    <customer>Northwind Cafe</customer>
                    <total>18.00</total>
                  </order>
                </orders>
                """;
        Document orders = DocumentHelper.parseText(ordersXml);

        Stylesheet router = new Stylesheet();
        XPath urgentOrders = DocumentHelper.createXPath("self::order[@priority='urgent']");
        XPath standardOrders = DocumentHelper.createXPath("self::order[@priority='standard']");
        router.addRule(new Rule(new DefaultPattern(urgentOrders), route("express")));
        router.addRule(new Rule(new DefaultPattern(standardOrders), route("scheduled")));

        XPath allOrders = DocumentHelper.createXPath("/orders/order");
        router.applyTemplates(orders, allOrders);

        List<Node> routedOrders = DocumentHelper.createXPath("/orders/order[routing]")
                .selectNodes(orders);
        assertEquals(2, routedOrders.size());
        assertEquals("express", ((Element) routedOrders.getFirst())
                .element("routing").elementText("service"));
        assertEquals("scheduled", ((Element) routedOrders.get(1))
                .element("routing").elementText("service"));
        assertEquals("Acme Bakery", ((Element) routedOrders.getFirst())
                .elementText("customer"));
        assertNotNull(((Element) routedOrders.get(1)).element("total"));
    }

    private static Action route(String service) {
        return node -> ((Element) node).addElement("routing").addElement("service")
                .addText(service);
    }
}
