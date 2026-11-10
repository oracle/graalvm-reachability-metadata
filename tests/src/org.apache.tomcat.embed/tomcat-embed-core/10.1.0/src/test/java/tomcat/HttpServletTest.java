/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.io.File;
import java.io.IOException;
import java.net.http.HttpResponse;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpServletTest {
    @TempDir
    File base;

    @Test
    void discoversImplementedMethodsForOptionsResponse() throws Exception {
        try (EmbeddedApplication application = new EmbeddedApplication(base)) {
            application.addServlet(new ReadOnlyServlet());
            application.start();
            HttpResponse<String> response = application.request("OPTIONS");
            assertEquals(200, response.statusCode());
            String allow = response.headers().firstValue("Allow").orElseThrow();
            assertTrue(allow.contains("GET"));
            assertTrue(allow.contains("HEAD"));
            assertTrue(allow.contains("OPTIONS"));
            assertFalse(allow.contains("POST"));
        }
    }

    public static class ReadOnlyServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.getOutputStream().print("content");
        }
    }
}
