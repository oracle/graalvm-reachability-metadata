/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.apache.catalina.manager.StatusTransformer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StatusTransformerTest {

    @Test
    void writesOsStateThroughPublicApi() {
        StringWriter output = new StringWriter();
        Object[] labels = {"total", "free", "available", "used", "memory", "up", "idle"};

        StatusTransformer.writeOSState(new PrintWriter(output), 0, labels);

        String result = output.toString();
        assertThat(result.isEmpty() || result.startsWith("<h1>OS</h1>")).isTrue();
    }
}
