/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.net.http.HttpResponse;
import java.nio.file.Path;

import jakarta.servlet.http.HttpServlet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpServletInnerTomcatHackTest {

    @Test
    void optionsHonorsTomcatConnectorTracePolicy(@TempDir Path baseDirectory) throws Exception {
        try (EmbeddedTomcatSupport server = new EmbeddedTomcatSupport(baseDirectory, new OptionsServlet())) {
            server.connector().setAllowTrace(false);
            server.start();

            HttpResponse<String> response = server.request("OPTIONS");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("Allow")).hasValueSatisfying(
                    value -> assertThat(value).contains("OPTIONS").doesNotContain("TRACE"));
        }
    }

    private static final class OptionsServlet extends HttpServlet {
    }
}
