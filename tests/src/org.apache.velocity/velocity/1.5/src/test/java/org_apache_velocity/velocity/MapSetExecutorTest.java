/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity;

import java.util.HashMap;
import java.util.Map;

import org.apache.velocity.VelocityContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MapSetExecutorTest {
    @Test
    void assignsMapEntriesFromTemplate() throws Exception {
        Map<String, String> customer = new HashMap<>();
        customer.put("name", "Atlas");
        VelocityContext context = new VelocityContext();
        context.put("customer", customer);
        assertEquals("Orion", VelocityTestSupport.render(context,
                "#set($customer.name = 'Orion')$customer.name"));
        assertEquals("Orion", customer.get("name"));
    }
}
