/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

public class TomcatTests {

    private static final int AUTO_BIND_PORT = 0;

    @ParameterizedTest
    @ValueSource(strings = {"HTTP/1.1", "org.apache.coyote.http11.Http11NioProtocol",
            "org.apache.coyote.http11.Http11Nio2Protocol"})
    void test(String protocol) throws Exception {
        Tomcat tomcat = new Tomcat();
        Connector connector = configureConnector(tomcat, protocol);
        assertProtocolIntrospection(connector);
        Context context = addContext(tomcat);
        addServlet(context);
        tomcat.start();
        try {
            assertProtocolIntrospection(connector);
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
            URI uri = URI.create("http://localhost:" + connector.getLocalPort() + "/hello");
            HttpRequest request = HttpRequest.newBuilder(uri).GET().header("Accept", "text/plain")
                    .timeout(Duration.ofSeconds(1)).build();
            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("Hello World\n");
        } finally {
            tomcat.stop();
            tomcat.destroy();
        }
    }

    private Context addContext(Tomcat tomcat) {
        Context context = tomcat.addContext("", new File(".").getAbsolutePath());
        Tomcat.addDefaultMimeTypeMappings(context);
        return context;
    }

    private void addServlet(Context context) {
        Tomcat.addServlet(context, "hello", new MyServlet());
        context.addServletMappingDecoded("/*", "hello");
    }

    private Connector configureConnector(Tomcat tomcat, String protocol) {
        Connector connector = new Connector(protocol);
        connector.setPort(AUTO_BIND_PORT);
        tomcat.setConnector(connector);
        return connector;
    }

    private void assertProtocolIntrospection(Connector connector) {
        assertThat(connector.getProperty("name")).isInstanceOf(String.class);
        assertThat(connector.getProperty("missingPropertyForReachabilityMetadata")).isNull();
        assertThat(connector.getProperty("SSLEnabled")).isEqualTo(Boolean.FALSE);
    }

    private static class MyServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setCharacterEncoding("UTF-8");
            resp.setContentType("text/plain");
            try (Writer writer = resp.getWriter()) {
                writer.write("Hello World\n");
            }
        }
    }
}
