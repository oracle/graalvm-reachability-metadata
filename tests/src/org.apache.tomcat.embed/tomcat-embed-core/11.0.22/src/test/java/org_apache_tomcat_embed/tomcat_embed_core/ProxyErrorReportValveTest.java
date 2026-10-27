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
import org.apache.catalina.valves.ProxyErrorReportValve;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class ProxyErrorReportValveTest {

    @Test
    void redirectsErrorsUsingLocaleSpecificProperties(@TempDir Path baseDirectory) throws Exception {
        ProxyErrorReportValve valve = new ProxyErrorReportValve();
        valve.setUsePropertiesFile(true);
        try (EmbeddedTomcatSupport server = new EmbeddedTomcatSupport(baseDirectory, new UnsupportedMethodServlet())) {
            server.replaceErrorReportValve(valve);
            server.start();

            HttpResponse<String> response = server.request("GET");

            assertThat(response.statusCode()).isEqualTo(302);
            assertThat(response.headers().firstValue("Location")).hasValueSatisfying(
                    value -> assertThat(value).contains("/errors/method-not-allowed", "statusCode=405"));
        }
    }

    private static final class UnsupportedMethodServlet extends HttpServlet {
    }
}
