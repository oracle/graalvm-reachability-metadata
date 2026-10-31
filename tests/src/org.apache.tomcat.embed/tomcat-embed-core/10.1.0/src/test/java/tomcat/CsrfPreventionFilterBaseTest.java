/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.io.File;
import java.io.IOException;
import java.net.CookieManager;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.filters.RestCsrfPreventionFilter;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CsrfPreventionFilterBaseTest {
    @TempDir
    File base;

    @Test
    void issuesNonceAndRequiresItForStateChangingRequests() throws Exception {
        try (EmbeddedApplication application = new EmbeddedApplication(base)) {
            application.addServlet(new ResourceServlet());
            FilterDef definition = new FilterDef();
            definition.setFilterName("csrf");
            definition.setFilter(new RestCsrfPreventionFilter());
            definition.setFilterClass(RestCsrfPreventionFilter.class.getName());
            application.context.addFilterDef(definition);
            FilterMap mapping = new FilterMap();
            mapping.setFilterName("csrf");
            mapping.addURLPattern("/*");
            application.context.addFilterMap(mapping);
            application.start();
            try (HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager())
                    .connectTimeout(Duration.ofSeconds(10)).build()) {
                HttpRequest fetch = HttpRequest.newBuilder(application.uri()).timeout(Duration.ofSeconds(10))
                        .header("X-CSRF-Token", "Fetch").GET().build();
                HttpResponse<String> fetched = client.send(fetch, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, fetched.statusCode());
                String nonce = fetched.headers().firstValue("X-CSRF-Token").orElseThrow();
                assertTrue(nonce.matches("[0-9A-F]{32}"));
                HttpRequest denied = HttpRequest.newBuilder(application.uri()).timeout(Duration.ofSeconds(10))
                        .POST(HttpRequest.BodyPublishers.noBody()).build();
                assertEquals(403, client.send(denied, HttpResponse.BodyHandlers.ofString()).statusCode());
                HttpRequest allowed = HttpRequest.newBuilder(application.uri()).timeout(Duration.ofSeconds(10))
                        .header("X-CSRF-Token", nonce).POST(HttpRequest.BodyPublishers.noBody()).build();
                HttpResponse<String> response = client.send(allowed, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, response.statusCode());
                assertEquals("updated", response.body());
            }
        }
    }

    public static class ResourceServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.getWriter().write("resource");
        }

        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.getWriter().write("updated");
        }
    }
}
