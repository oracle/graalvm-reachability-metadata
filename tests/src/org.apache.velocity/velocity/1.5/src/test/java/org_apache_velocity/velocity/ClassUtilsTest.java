/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity;

import java.io.InputStream;
import java.util.Properties;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.util.ClassUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ClassUtilsTest {
    @Test
    void instantiatesContextAndReadsClasspathProperties() throws Exception {
        assertSame(VelocityContext.class, ClassUtils.getClass(VelocityContext.class.getName()));
        VelocityContext context = (VelocityContext) ClassUtils.getNewInstance(VelocityContext.class.getName());
        context.put("customer", "Atlas");
        assertEquals("Atlas", context.get("customer"));
        assertSettings();
        try (InputStream missing = ClassUtils.getResourceAsStream(VelocityContext.class, "velocity/absent.properties")) {
            assertNull(missing);
        }
    }

    @Test
    void resolvesClassesAndResourcesWithoutAThreadContextLoader() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(null);
            VelocityContext context = (VelocityContext) ClassUtils.getNewInstance(VelocityContext.class.getName());
            context.put("customer", "Orion");
            assertEquals("Orion", context.get("customer"));
            assertSettings();
        } finally {
            thread.setContextClassLoader(original);
        }
    }

    private static void assertSettings() throws Exception {
        try (InputStream input = ClassUtils.getResourceAsStream(VelocityContext.class, "/velocity/settings.properties")) {
            assertNotNull(input);
            Properties settings = new Properties();
            settings.load(input);
            assertEquals("Atlas", settings.getProperty("projectName"));
        }
    }
}
