/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_tomcat;

import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRegistration;
import jakarta.servlet.jsp.JspFactory;
import org.apache.jasper.compiler.TldCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class TomcatServletWebServerFactoryTest {
    @Test
    void initializesJasperAndRegistersJspServletMappings() {
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory(0);
        AtomicReference<ServletContext> initializedContext = new AtomicReference<>();
        WebServer server = factory.getWebServer(initializedContext::set);
        try {
            server.start();
            ServletContext servletContext = initializedContext.get();
            assertNotNull(servletContext);
            // Jasper's initializer publishes this cache before application initializers run.
            assertInstanceOf(TldCache.class,
                    servletContext.getAttribute(TldCache.SERVLET_CONTEXT_ATTRIBUTE_NAME));
            JspFactory jspFactory = JspFactory.getDefaultFactory();
            assertNotNull(jspFactory);
            assertNotNull(jspFactory.getJspApplicationContext(servletContext));
            ServletRegistration jspServlet = servletContext.getServletRegistration("jsp");
            assertNotNull(jspServlet);
            assertEquals("org.apache.jasper.servlet.JspServlet", jspServlet.getClassName());
            assertTrue(jspServlet.getMappings().contains("*.jsp"));
            assertTrue(jspServlet.getMappings().contains("*.jspx"));
        } finally {
            try {
                server.stop();
            } finally {
                server.destroy();
            }
        }
    }
}
