/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity;

import java.io.StringWriter;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResourceLoaderTest {
    @Test
    void configuresApplicationSuppliedResourceLoader() throws Exception {
        ClasspathResourceLoader loader = new ClasspathResourceLoader();
        VelocityEngine engine = VelocityTestSupport.configuredEngine();
        engine.setProperty("class.resource.loader.instance", loader);
        engine.setProperty("class.resource.loader.cache", true);
        engine.setProperty("class.resource.loader.modificationCheckInterval", "30");
        engine.init();
        assertTrue(loader.isCachingOn());
        assertEquals(30L, loader.getModificationCheckInterval());
        assertEquals(ClasspathResourceLoader.class.getName(), loader.getClassName());
        VelocityContext context = new VelocityContext();
        context.put("name", "Atlas");
        StringWriter output = new StringWriter();
        engine.getTemplate("velocity/greeting.vm", "UTF-8").merge(context, output);
        assertEquals("Hello Atlas!\n", output.toString());
    }
}
