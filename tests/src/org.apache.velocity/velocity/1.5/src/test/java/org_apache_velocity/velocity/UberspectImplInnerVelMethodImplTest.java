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

public class UberspectImplInnerVelMethodImplTest {
    @Test
    void invokesContextObjectMethodsWithArguments() throws Exception {
        StringBuilder message = new StringBuilder("Hello");
        VelocityContext context = new VelocityContext();
        context.put("message", message);
        assertEquals("Hello Atlas|11", VelocityTestSupport.render(context,
                "$message.append(' Atlas')|$message.length()"));
        assertEquals("Hello Atlas", message.toString());
    }
}
