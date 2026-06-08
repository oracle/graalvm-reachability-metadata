/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_wiremock.wiremock;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.HttpServerFactory;
import com.github.tomakehurst.wiremock.standalone.CommandLineOptions;
import org.junit.jupiter.api.Test;

public class CommandLineOptionsTest {
    @Test
    void httpServerFactoryInstantiatesDefaultJettyFactory() {
        CommandLineOptions options = new CommandLineOptions("--port", "0");

        HttpServerFactory factory = options.httpServerFactory();

        assertThat(factory).isNotNull();
        assertThat(factory.getClass().getName())
                .isEqualTo("com.github.tomakehurst.wiremock.jetty.JettyHttpServerFactory");
    }
}
