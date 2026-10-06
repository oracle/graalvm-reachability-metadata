/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.servlet.ServletContext;
import org.apache.catalina.Context;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class ApplicationContextFacadeTest {

    @Test
    void exposesServletContextOperationsThroughTheEmbeddedContainer(@TempDir Path baseDirectory) throws Exception {
        Files.writeString(baseDirectory.resolve("resource.txt"), "tomcat");

        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(baseDirectory.resolve("base").toString());
        Context context = tomcat.addContext("", baseDirectory.toString());
        EmbeddedTomcatSupport.configureContextWithoutWebappScanning(context);
        Tomcat.addServlet(context, "default", new DefaultServlet());
        context.addServletMappingDecoded("/default", "default");
        context.addMimeMapping("txt", "text/plain");
        tomcat.start();

        try {
            ServletContext servletContext = context.getServletContext();
            servletContext.setAttribute("answer", 42);

            assertThat(servletContext.getAttribute("answer")).isEqualTo(42);
            assertThat(servletContext.getMimeType("resource.txt")).isEqualTo("text/plain");
            assertThat(servletContext.getResource("/resource.txt")).isNotNull();
            try (InputStream resource = servletContext.getResourceAsStream("/resource.txt")) {
                assertThat(resource).hasContent("tomcat");
            }
            assertThat(servletContext.getServlet("missing")).isNull();
            assertThat(servletContext.getServletRegistration("default")).isNotNull();
            assertThat(servletContext.getServletRegistrations()).containsKey("default");
            assertThat(servletContext.createServlet(DefaultServlet.class)).isInstanceOf(DefaultServlet.class);
        } finally {
            tomcat.stop();
            tomcat.destroy();
        }
    }
}
