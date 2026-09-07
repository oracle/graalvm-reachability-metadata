/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.trilead.ssh2.transport.TransportConnection;

import static org.assertj.core.api.Assertions.assertThat;

public class TransportConnectionTest {
    @Test
    void reportsPacketOverheadForAnUnencryptedTransport() {
        TransportConnection connection = new TransportConnection(
            new ByteArrayInputStream(new byte[0]),
            new ByteArrayOutputStream(),
            new SecureRandom()
        );

        assertThat(connection.getPacketOverheadEstimate()).isPositive();
    }
}
