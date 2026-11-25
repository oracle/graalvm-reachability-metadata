/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpServletTest {
    @TempDir
    Path directory;

    @Test
    void derivesAllowedMethodsFromServletOverrides() throws Exception {
        try (EmbeddedServer server = new EmbeddedServer(directory)) {
            server.servlet(new ReadServlet());
            server.start();
            HttpResponse<String> response = server.request("OPTIONS", "/read");
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("Allow").orElseThrow()).contains("GET", "HEAD", "OPTIONS")
                    .doesNotContain("POST");
        }
    }

    public static class ReadServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.getWriter().write("readable");
        }
    }
}
