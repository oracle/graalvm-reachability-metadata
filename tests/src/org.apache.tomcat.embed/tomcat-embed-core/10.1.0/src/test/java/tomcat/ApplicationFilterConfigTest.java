/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.filters.AddDefaultCharsetFilter;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class ApplicationFilterConfigTest {
    @TempDir
    Path directory;

    @Test
    void initializesNamedFilterAndMaintainsCookieBackedSessions() throws Exception {
        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(directory.resolve("server").toString());
        tomcat.setPort(0);
        Connector connector = tomcat.getConnector();
        connector.setProperty("address", "127.0.0.1");
        Context context = tomcat.addContext("/catalog",
                Files.createDirectory(directory.resolve("webapp")).toString());
        Tomcat.addServlet(context, "visits", new VisitServlet());
        context.addServletMappingDecoded("/visits", "visits");

        FilterDef filter = new FilterDef();
        filter.setFilterName("charset");
        filter.setFilterClass(AddDefaultCharsetFilter.class.getName());
        filter.addInitParameter("encoding", "UTF-8");
        context.addFilterDef(filter);
        FilterMap mapping = new FilterMap();
        mapping.setFilterName("charset");
        mapping.addURLPattern("/*");
        context.addFilterMap(mapping);

        try {
            tomcat.start();
            URI endpoint = URI.create("http://127.0.0.1:" + connector.getLocalPort() + "/catalog/visits");
            try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
                HttpResponse<String> first = client.send(HttpRequest.newBuilder(endpoint)
                        .timeout(Duration.ofSeconds(10)).GET().build(), BodyHandlers.ofString());
                assertThat(first.statusCode()).isEqualTo(200);
                assertThat(first.headers().firstValue("Content-Type")).hasValue("text/plain;charset=UTF-8");
                assertThat(first.body()).isEqualTo("caf\u00e9:1");
                String cookie = first.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
                assertThat(cookie).startsWith("JSESSIONID=");

                HttpResponse<String> returning = client.send(HttpRequest.newBuilder(endpoint)
                        .header("Cookie", cookie).timeout(Duration.ofSeconds(10)).GET().build(),
                        BodyHandlers.ofString());
                assertThat(returning.statusCode()).isEqualTo(200);
                assertThat(returning.body()).isEqualTo("caf\u00e9:2");

                HttpResponse<String> independent = client.send(HttpRequest.newBuilder(endpoint)
                        .timeout(Duration.ofSeconds(10)).GET().build(), BodyHandlers.ofString());
                assertThat(independent.statusCode()).isEqualTo(200);
                assertThat(independent.body()).isEqualTo("caf\u00e9:1");
            }
        } finally {
            try {
                tomcat.stop();
            } finally {
                tomcat.destroy();
            }
        }
    }

    public static class VisitServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            HttpSession session = request.getSession();
            Integer previous = (Integer) session.getAttribute("visits");
            int visits = previous == null ? 1 : previous + 1;
            session.setAttribute("visits", visits);
            response.setContentType("text/plain");
            response.getWriter().write("caf\u00e9:" + visits);
        }
    }
}
