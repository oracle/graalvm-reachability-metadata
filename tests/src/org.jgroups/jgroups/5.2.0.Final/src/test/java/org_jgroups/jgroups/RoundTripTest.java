/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.jgroups.tests.RoundTrip;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RoundTripTest {
    @Test
    void printsHelpForTcpTransportAlias() throws Exception {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            RoundTrip.main(new String[] {"-tp", "tcp", "-h"});
        } finally {
            System.setOut(originalOut);
        }

        String help = output.toString(StandardCharsets.UTF_8);
        assertThat(help).contains("RoundTrip", "-host <host>", "-port <port>", "-server", "-tcp-nodelay");
    }
}
