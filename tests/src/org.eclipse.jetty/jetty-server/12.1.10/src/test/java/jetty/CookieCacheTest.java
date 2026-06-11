/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jetty;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.server.NetworkConnector;
import org.eclipse.jetty.server.Server;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CookieCacheTest {
    @Test
    void convertsRequestCookiesThroughServletApi() throws Exception {
        Server server = new Server(0);
        ServletContextHandler handler = new ServletContextHandler();
        handler.setContextPath("/");
        handler.addServlet(CookieReadingServlet.class, "/*");
        server.setHandler(handler);

        server.start();
        try {
            HttpResponse<String> response = doHttpRequest(server);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("session=cookie-value");
        } finally {
            server.stop();
        }
    }

    private static HttpResponse<String> doHttpRequest(Server server) throws IOException, InterruptedException {
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()) {
            HttpRequest request = HttpRequest.newBuilder(localUri(server))
                    .GET()
                    .header("Cookie", "session=cookie-value; other=other-value")
                    .timeout(Duration.ofSeconds(2))
                    .build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    private static URI localUri(Server server) {
        NetworkConnector connector = (NetworkConnector) server.getConnectors()[0];
        return URI.create("http://localhost:" + connector.getLocalPort() + "/");
    }

    public static class CookieReadingServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            Cookie[] cookies = request.getCookies();
            String value = Arrays.stream(cookies)
                    .filter(cookie -> "session".equals(cookie.getName()))
                    .findFirst()
                    .map(Cookie::getValue)
                    .orElseThrow();

            response.setStatus(200);
            response.setHeader("Content-Type", "text/plain");
            response.getWriter().print("session=" + value);
            response.getWriter().flush();
        }
    }
}
