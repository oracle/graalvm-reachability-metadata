/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpServletInnerNoBodyOutputStreamTest {

    @Test
    void headCountsGeneratedContentWithoutReturningABody(@TempDir Path baseDirectory) throws Exception {
        try (EmbeddedTomcatSupport server = new EmbeddedTomcatSupport(baseDirectory, new ContentServlet())) {
            server.addServletInitParameter(HttpServlet.LEGACY_DO_HEAD, Boolean.TRUE.toString());
            server.start();

            HttpResponse<String> response = server.request("HEAD");

            assertThat(response.statusCode()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(response.body()).isEmpty();
            assertThat(response.headers().firstValueAsLong("Content-Length")).hasValue(7);
        }
    }

    private static final class ContentServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.getOutputStream().print("content");
        }
    }
}
