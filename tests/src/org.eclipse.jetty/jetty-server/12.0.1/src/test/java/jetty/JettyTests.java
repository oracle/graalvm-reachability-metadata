/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jetty;

import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.webapp.WebAppContext;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.TypeUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

public class JettyTests {

    private static final int PORT = 8080;

    private static boolean DEBUG = false;

    @BeforeAll
    static void beforeAll() {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", DEBUG ? "debug" : "warn");
    }

    @Test
    void typeUtilWorks() {
        assertThat(TypeUtil.fromName("java.lang.String")).isEqualTo(String.class);
    }

    @Test
    void http() throws Exception {
        Server server = new Server(PORT);
        server.setHandler(new Handler.Abstract() {
            @Override
            public boolean handle(Request request, Response response, Callback callback) {
                response.setStatus(200);
                response.getHeaders().add("Content-Type", "text/plain");
                response.write(true, ByteBuffer.wrap("Hello world".getBytes(StandardCharsets.UTF_8)), callback);
                return true;
            }
        });
        server.start();
        try {
            HttpResponse<String> response = doHttpRequest();
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("Hello world");
        } finally {
            server.stop();
        }
    }

    @Test
    void servlet() throws Exception {
        Server server = new Server(PORT);
        ServletContextHandler handler = new ServletContextHandler();
        handler.setContextPath("/");
        handler.addServlet(HelloWorldServlet.class, "/*");
        server.setHandler(handler);
        server.start();
        try {
            HttpResponse<String> response = doHttpRequest();
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("Hello world");
        } finally {
            server.stop();
        }
    }

    @Test
    // This test doesn't work in a native image and fails with:
    // java.lang.IllegalArgumentException: not an allowed scheme: resource:/org/eclipse/jetty/ee10/webapp/webdefault-ee10.xml
    @DisabledInNativeImage
    void webapp(@TempDir Path tempDir) throws Exception {
        Server server = new Server(PORT);
        WebAppContext context = new WebAppContext();
        // EnvConfiguration and PlusConfiguration uses JNDI, which is not what we want to include
        context.setBaseResourceAsPath(tempDir);
        context.setContextPath("/");
        context.getServletContext().addServlet("HelloWorld", HelloWorldServlet.class).addMapping("/*");
        server.setHandler(context);
        server.start();
        try {
            HttpResponse<String> response = doHttpRequest();
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("Hello world");
        } finally {
            server.stop();
        }
    }

    private static HttpResponse<String> doHttpRequest(String... headers) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(String.format("http://localhost:%d/", PORT)))
                .GET().header("Accept", "text/plain").timeout(Duration.ofSeconds(1));
        if (headers.length > 0) {
            request.headers(headers);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
