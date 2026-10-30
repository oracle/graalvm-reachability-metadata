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

public class MapGetExecutorTest {
    @Test
    void readsMapEntriesWithPropertyNotation() throws Exception {
        Map<String, String> customer = new HashMap<>();
        customer.put("name", "Atlas");
        customer.put("region", "West");
        VelocityContext context = new VelocityContext();
        context.put("customer", customer);
        assertEquals("Atlas:West", VelocityTestSupport.render(context, "$customer.name:$customer.region"));
    }
}
