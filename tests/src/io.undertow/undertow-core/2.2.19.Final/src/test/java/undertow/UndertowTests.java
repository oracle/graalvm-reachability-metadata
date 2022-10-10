/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package undertow;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.util.ImmediateInstanceFactory;
import io.undertow.util.Headers;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

public class UndertowTests {

    private static final int PORT = 8080;

    @Test
    void core() throws Exception {
        // Start server
        Undertow server = Undertow.builder()
                .addHttpListener(PORT, "localhost")
                .setHandler(exchange -> {
                    exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
                    exchange.getResponseSender().send("Hello World");
                }).build();
        server.start();
        try {
            // Make request
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(String.format("http://localhost:%d/", PORT)))
                    .GET().header("Accept", "text/plain").timeout(Duration.ofSeconds(1)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("Hello World");
        } finally {
            // Cleanup
            server.stop();
        }
    }

    @Test
    void servlet() throws Exception {
        // Start server
        DeploymentInfo servletBuilder = Servlets.deployment()
                .setClassLoader(MessageServlet.class.getClassLoader())
                .setContextPath("/myapp")
                .setDeploymentName("test")
                .addServlets(
                        Servlets.servlet("MessageServlet", MessageServlet.class, new ImmediateInstanceFactory<>(new MessageServlet())).addMapping("/hello")
                );
        DeploymentManager manager = Servlets.defaultContainer().addDeployment(servletBuilder);
        manager.deploy();
        PathHandler path = Handlers.path(Handlers.redirect("/myapp"))
                .addPrefixPath("/myapp", manager.start());
        Undertow server = Undertow.builder()
                .addHttpListener(PORT, "localhost")
                .setHandler(path)
                .build();
        server.start();
        try {
            // Make request
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(String.format("http://localhost:%d/myapp/hello", PORT)))
                    .GET().header("Accept", "text/plain").timeout(Duration.ofSeconds(1)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("Hello world");
        } finally {
            // Cleanup
            server.stop();
        }
    }
}
