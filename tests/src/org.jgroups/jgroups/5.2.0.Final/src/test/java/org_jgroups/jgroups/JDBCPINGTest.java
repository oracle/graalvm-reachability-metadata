/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.protocols.JDBC_PING;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatCode;

public class JDBCPINGTest {
    @Test
    void loadsConfiguredDriverClass() {
        ExposedJDBCPING protocol = new ExposedJDBCPING();

        assertThatCode(() -> protocol.loadConfiguredDriver(String.class.getName()))
            .doesNotThrowAnyException();
    }

    @Test
    void commandLineUtilityLoadsConfiguredDriverClass() {
        PrintStream originalErr = System.err;
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        try {
            System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));

            assertThatCode(() -> JDBC_PING.main(new String[] {
                "-driver", String.class.getName(),
                "-conn", "jdbc:jgroups-test:unavailable"
            })).doesNotThrowAnyException();
        } finally {
            System.setErr(originalErr);
        }
    }
}

final class ExposedJDBCPING extends JDBC_PING {
    void loadConfiguredDriver(String driverClassName) {
        connection_driver = driverClassName;
        loadDriver();
    }
}
