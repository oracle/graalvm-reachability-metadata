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

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.server.Server;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CookieCacheTest {

    @Test
    void convertsRequestCookiesThroughServletApi() throws Exception {
        Server server = new Server(0);
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        context.addServlet(CookieServlet.class, "/*");
        server.setHandler(context);
        server.start();
        try {
            HttpResponse<String> response = doHttpRequest(server.getURI(), "Cookie", "flavor=vanilla");
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("flavor=vanilla");
        } finally {
            server.stop();
        }
    }

    private static HttpResponse<String> doHttpRequest(URI uri, String... headers)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(Duration.ofSeconds(10));
        request.headers(headers);
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    public static class CookieServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            Cookie cookie = request.getCookies()[0];
            response.setStatus(200);
            response.setContentType("text/plain");
            response.getWriter().print(cookie.getName() + "=" + cookie.getValue());
            response.getWriter().flush();
        }
    }
}
