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
import org.apache.catalina.Wrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpServletInnerNoBodyOutputStreamTest {
    @TempDir
    Path directory;

    @Test
    void legacyHeadCountsBytesWithoutSendingBody() throws Exception {
        try (EmbeddedServer server = new EmbeddedServer(directory)) {
            Wrapper wrapper = server.servlet(new HeadServlet());
            wrapper.addInitParameter("jakarta.servlet.http.legacyDoHead", "true");
            server.start();
            HttpResponse<String> response = server.request("HEAD", "/");
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEmpty();
            assertThat(response.headers().firstValue("Content-Length").orElseThrow()).isEqualTo("7");
        }
    }

    public static class HeadServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.getOutputStream().print("payload");
        }
    }
}
