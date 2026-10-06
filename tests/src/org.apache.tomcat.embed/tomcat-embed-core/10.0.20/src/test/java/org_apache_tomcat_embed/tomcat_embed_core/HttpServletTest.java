/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpServletTest {

    @Test
    void optionsReportsMethodsImplementedByServlet(@TempDir Path directory) throws Exception {
        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(directory.resolve("base").toString());
        Connector connector = new Connector();
        connector.setPort(0);
        tomcat.setConnector(connector);
        Context context = tomcat.addContext("", directory.toString());
        Tomcat.addServlet(context, "options", new GetServlet());
        context.addServletMappingDecoded("/options", "options");

        try {
            tomcat.start();
            HttpRequest request = HttpRequest.newBuilder(
                    URI.create("http://localhost:" + connector.getLocalPort() + "/options"))
                    .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).timeout(Duration.ofSeconds(10)).build();
            HttpResponse<Void> response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
                    .send(request, HttpResponse.BodyHandlers.discarding());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("allow")).hasValueSatisfying(
                    allow -> assertThat(allow).contains("GET", "HEAD", "OPTIONS"));
        } finally {
            tomcat.stop();
            tomcat.destroy();
        }
    }

    public static class GetServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        }
    }
}
