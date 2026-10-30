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

public class SetPropertyExecutorTest {
    @Test
    void updatesBeanThroughTemplateAssignment() throws Exception {
        Customer customer = new Customer();
        VelocityContext context = new VelocityContext();
        context.put("customer", customer);
        assertEquals("Orion", VelocityTestSupport.render(context,
                "#set($customer.name = 'Orion')$customer.name"));
        assertEquals("Orion", customer.getName());
    }

    public static class Customer {
        private String name = "Atlas";

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
