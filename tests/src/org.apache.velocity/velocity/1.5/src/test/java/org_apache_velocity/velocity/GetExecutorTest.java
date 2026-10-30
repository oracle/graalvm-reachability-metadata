/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity;

import java.util.Properties;

import org.apache.velocity.VelocityContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GetExecutorTest {
    @Test
    void readsPropertiesThroughGenericGetMethod() throws Exception {
        Properties settings = new Properties();
        settings.setProperty("title", "Weekly report");
        VelocityContext context = new VelocityContext();
        context.put("settings", settings);
        assertEquals("Weekly report", VelocityTestSupport.render(context, "$settings.title"));
    }
}
