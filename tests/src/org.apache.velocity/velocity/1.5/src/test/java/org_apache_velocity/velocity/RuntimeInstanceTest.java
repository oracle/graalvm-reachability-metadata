/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity;

import java.io.StringWriter;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.runtime.RuntimeInstance;
import org.apache.velocity.runtime.log.NullLogChute;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RuntimeInstanceTest {
    @Test
    void initializesDefaultConfigurationAndRendersClasspathTemplate() throws Exception {
        RuntimeInstance runtime = new RuntimeInstance();
        runtime.setProperty("runtime.log.logsystem", new NullLogChute());
        runtime.setProperty("velocimacro.library", "");
        runtime.setProperty("resource.loader", "class");
        runtime.setProperty("class.resource.loader.class", ClasspathResourceLoader.class.getName());
        runtime.init();

        assertTrue(runtime.isInitialized());
        VelocityContext context = new VelocityContext();
        context.put("name", "Atlas");
        context.put("title", "Monthly");
        StringWriter output = new StringWriter();
        runtime.getTemplate("velocity/page.vm", "UTF-8").merge(context, output);
        assertEquals("Title: Monthly\nHello Atlas!\n", output.toString());
    }
}
