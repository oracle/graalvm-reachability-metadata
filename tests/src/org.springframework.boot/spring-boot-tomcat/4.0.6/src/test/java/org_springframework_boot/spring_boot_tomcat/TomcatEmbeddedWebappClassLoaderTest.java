/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_tomcat;

import jakarta.servlet.Servlet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.tomcat.TomcatEmbeddedWebappClassLoader;

import static org.junit.jupiter.api.Assertions.assertSame;

@Timeout(120)
public class TomcatEmbeddedWebappClassLoaderTest {
    @Test
    void delegatesServletApiToTheConfiguredParent() throws Exception {
        try (TomcatEmbeddedWebappClassLoader loader =
                     new TomcatEmbeddedWebappClassLoader(Servlet.class.getClassLoader())) {
            // Servlet API classes are parent-first even with local-first delegation configured.
            loader.setDelegate(false);
            Class<?> servletType = loader.loadClass("jakarta.servlet.Servlet", true);
            assertSame(Servlet.class, servletType);
            assertSame(servletType, loader.loadClass("jakarta.servlet.Servlet", false));
        }
    }

    @Test
    void resolvesParentClassesWhenDelegationIsEnabled() throws Exception {
        try (TomcatEmbeddedWebappClassLoader loader =
                     new TomcatEmbeddedWebappClassLoader(Servlet.class.getClassLoader())) {
            loader.setDelegate(true);
            assertSame(String.class, loader.loadClass("java.lang.String", true));
            assertSame(Servlet.class, loader.loadClass("jakarta.servlet.Servlet", false));
        }
    }
}
