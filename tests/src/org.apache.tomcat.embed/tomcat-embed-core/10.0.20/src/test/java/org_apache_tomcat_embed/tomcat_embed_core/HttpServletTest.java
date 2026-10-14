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

public class HttpServletTest {

    @Test
    void optionsDescribesMethodsImplementedByServletSubclass(@TempDir Path baseDirectory) throws Exception {
        try (EmbeddedTomcatSupport server = new EmbeddedTomcatSupport(baseDirectory, new ReadServlet())) {
            server.start();

            HttpResponse<String> response = server.request("OPTIONS");

            assertThat(response.statusCode()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(response.headers().firstValue("Allow")).hasValueSatisfying(
                    value -> assertThat(value).contains("GET", "HEAD", "OPTIONS").doesNotContain("POST"));
        }
    }

    private static final class ReadServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.getWriter().write("read");
        }
    }
}
