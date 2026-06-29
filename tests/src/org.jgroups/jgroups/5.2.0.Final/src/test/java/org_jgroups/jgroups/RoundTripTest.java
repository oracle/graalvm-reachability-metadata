/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.tests.RoundTrip;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class RoundTripTest {
    @Test
    void helpCreatesConfiguredTransportAndPrintsItsOptions() throws Exception {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (PrintStream replacementOut = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(replacementOut);

            RoundTrip.main(new String[] {"-tp", "tcp", "-h"});
        }
        finally {
            System.setOut(originalOut);
        }

        String help = output.toString(StandardCharsets.UTF_8);
        assertThat(help)
                .contains("RoundTrip")
                .contains("tcp")
                .contains("-host <host>")
                .contains("-port <port>")
                .contains("-server")
                .contains("-tcp-nodelay");
    }
}
