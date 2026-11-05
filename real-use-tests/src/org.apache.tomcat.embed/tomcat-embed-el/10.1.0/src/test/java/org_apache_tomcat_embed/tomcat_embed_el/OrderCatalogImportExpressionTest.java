/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_el;

import jakarta.el.ELProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Resolves imported application static members with Jakarta Expression Language.
 * An order service can keep catalog rules in ordinary Java code while allowing operations to
 * select the rule with an expression maintained in its deployment configuration.
 */
public class OrderCatalogImportExpressionTest {

    @Test
    @Timeout(60)
    public void evaluatesOrderRuleThroughImportedStaticMethod() {
        ELProcessor expressions = new ELProcessor();
        expressions.getELManager().importStatic(OrderCatalog.class.getName() + ".DEFAULT_REGION");
        expressions.getELManager().importStatic(OrderCatalog.class.getName() + ".ruleFor");
        expressions.defineBean("order", new Order("EU", 185));

        String region = expressions.eval("DEFAULT_REGION");
        String rule = expressions.eval("ruleFor(order.region, order.total)");

        assertEquals("EU", region);
        assertEquals("priority-eu", rule);
    }

    /** Application catalog rules imported into the expression context. */
    public static final class OrderCatalog {
        public static final String DEFAULT_REGION = "EU";

        public static String ruleFor(String region, int total) {
            if (DEFAULT_REGION.equals(region) && total >= 150) {
                return "priority-eu";
            }
            return "standard";
        }
    }

    /** Order fields exposed to the configured catalog expression. */
    public static final class Order {
        private final String region;
        private final int total;

        public Order(String region, int total) {
            this.region = region;
            this.total = total;
        }

        public String getRegion() {
            return region;
        }

        public int getTotal() {
            return total;
        }
    }
}
