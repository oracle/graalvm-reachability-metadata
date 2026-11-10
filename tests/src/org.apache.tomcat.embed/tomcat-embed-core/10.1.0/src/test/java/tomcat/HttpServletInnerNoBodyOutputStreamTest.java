/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.io.File;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HttpServletInnerNoBodyOutputStreamTest {
    @TempDir
    File base;

    @Test
    void headReportsGetLengthWithoutSendingTheBody() throws Exception {
        try (EmbeddedApplication application = new EmbeddedApplication(base)) {
            application.addServlet(new HttpServletTest.ReadOnlyServlet());
            application.start();
            HttpResponse<String> response = application.request("HEAD");
            assertEquals(200, response.statusCode());
            assertEquals("", response.body());
            assertEquals("7", response.headers().firstValue("Content-Length").orElseThrow());
        }
    }
}
