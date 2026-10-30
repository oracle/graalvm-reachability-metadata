/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity;

import java.io.StringWriter;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.resource.ResourceCacheImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ResourceManagerImplTest {
    @Test
    void cachesTemplateWithConfiguredResourceCache() throws Exception {
        VelocityEngine engine = VelocityTestSupport.configuredEngine();
        engine.setProperty("resource.manager.cache.class", ResourceCacheImpl.class.getName());
        engine.setProperty("class.resource.loader.cache", true);
        engine.init();
        Template first = engine.getTemplate("velocity/greeting.vm", "UTF-8");
        assertSame(first, engine.getTemplate("velocity/greeting.vm", "UTF-8"));
        VelocityContext context = new VelocityContext();
        context.put("name", "Orion");
        StringWriter output = new StringWriter();
        first.merge(context, output);
        assertEquals("Hello Orion!\n", output.toString());
    }
}
