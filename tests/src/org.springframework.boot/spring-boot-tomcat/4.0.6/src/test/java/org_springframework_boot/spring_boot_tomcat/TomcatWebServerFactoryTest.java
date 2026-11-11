/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_tomcat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServer;
import org.springframework.context.support.GenericApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class TomcatWebServerFactoryTest {
    @Test
    void springConfiguresInheritedFactoryPropertiesAndServesRequests() throws Exception {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBeanDefinition("tomcatFactory", BeanDefinitionBuilder
                    .genericBeanDefinition(TomcatServletWebServerFactory.class,
                            TomcatServletWebServerFactory::new)
                    .addPropertyValue("port", 0)
                    .addPropertyValue("serverHeader", "metadata-tomcat")
                    .getBeanDefinition());
            context.refresh();
            TomcatServletWebServerFactory factory =
                    context.getBean("tomcatFactory", TomcatServletWebServerFactory.class);
            WebServer server = factory.getWebServer(servletContext -> servletContext
                    .addServlet("greeting", new HttpServlet() {
                        @Override
                        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                                throws IOException {
                            response.setContentType("text/plain");
                            response.getWriter().write("Hello from embedded Tomcat");
                        }
                    }).addMapping("/greeting"));
            try {
                server.start();
                assertTrue(server.getPort() > 0);
                try (HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(30)).build()) {
                    HttpRequest request = HttpRequest.newBuilder(
                                    URI.create("http://localhost:" + server.getPort() + "/greeting"))
                            .timeout(Duration.ofSeconds(30)).GET().build();
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    assertEquals(200, response.statusCode());
                    assertEquals("Hello from embedded Tomcat", response.body());
                    assertEquals("metadata-tomcat", response.headers().firstValue("server").orElseThrow());
                }
            } finally {
                try {
                    server.stop();
                } finally {
                    server.destroy();
                }
            }
        }
    }
}
