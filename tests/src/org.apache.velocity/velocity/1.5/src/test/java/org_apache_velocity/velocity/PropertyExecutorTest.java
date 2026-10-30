/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity;

import org.apache.velocity.VelocityContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PropertyExecutorTest {
    @Test
    void readsBeanGetterUsingPropertyNotation() throws Exception {
        VelocityContext context = new VelocityContext();
        context.put("customer", new Customer("Atlas"));
        assertEquals("Customer: Atlas", VelocityTestSupport.render(context, "Customer: $customer.name"));
    }

    public static class Customer {
        private final String name;

        public Customer(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
