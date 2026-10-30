/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity;

import java.util.List;

import org.apache.velocity.VelocityContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ASTDirectiveTest {
    @Test
    void instantiatesForeachDirectiveAndRendersItsBody() throws Exception {
        VelocityContext context = new VelocityContext();
        context.put("customers", List.of("Atlas", "Orion"));
        assertEquals("1:Atlas;2:Orion;", VelocityTestSupport.render(context,
                "#foreach($customer in $customers)$velocityCount:$customer;#end"));
    }
}
