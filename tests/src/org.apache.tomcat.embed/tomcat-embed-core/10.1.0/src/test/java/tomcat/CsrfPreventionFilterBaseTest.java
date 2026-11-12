/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;

import org.apache.catalina.filters.RestCsrfPreventionFilter;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class CsrfPreventionFilterBaseTest {
    @TempDir
    Path directory;

    @Test
    void generatesNonceThroughConfiguredRestCsrfFilter() throws Exception {
        try (EmbeddedServer server = new EmbeddedServer(directory)) {
            FilterDef definition = new FilterDef();
            definition.setFilterName("csrf");
            definition.setFilter(new RestCsrfPreventionFilter());
            server.context().addFilterDef(definition);
            FilterMap mapping = new FilterMap();
            mapping.setFilterName("csrf");
            mapping.addURLPattern("/*");
            server.context().addFilterMap(mapping);
            server.servlet(new HttpServletTest.ReadServlet());
            server.start();
            URI uri = URI.create("http://localhost:" + server.tomcat().getConnector().getLocalPort() + "/");
            try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
                HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                        .header("X-CSRF-Token", "Fetch").GET().build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.headers().firstValue("X-CSRF-Token").orElseThrow()).matches("[0-9A-F]{32}");
                assertThat(response.body()).isEqualTo("readable");
            }
        }
    }
}
