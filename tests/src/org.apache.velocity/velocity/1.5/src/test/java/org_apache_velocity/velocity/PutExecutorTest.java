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

public class PutExecutorTest {
    @Test
    void assignsPropertyThroughGenericPutMethod() throws Exception {
        Properties settings = new Properties();
        settings.setProperty("title", "Draft");
        VelocityContext context = new VelocityContext();
        context.put("settings", settings);
        assertEquals("Published", VelocityTestSupport.render(context,
                "#set($settings.title = 'Published')$settings.title"));
        assertEquals("Published", settings.getProperty("title"));
    }
}
