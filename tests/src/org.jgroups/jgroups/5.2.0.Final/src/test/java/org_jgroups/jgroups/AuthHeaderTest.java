/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.auth.RegexMembership;
import org.jgroups.conf.ClassConfigurator;
import org.jgroups.protocols.AuthHeader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class AuthHeaderTest {
    static {
        configureJGroupsLoopbackDefaults();
    }

    @BeforeAll
    static void configureLoopbackDefaults() {
        configureJGroupsLoopbackDefaults();
    }

    @Test
    void roundTripsUnregisteredAuthTokenByClassName() throws Exception {
        RegexMembership token = new RegexMembership();
        assertThat(ClassConfigurator.getMagicNumber(RegexMembership.class)).isEqualTo((short) -1);
        AuthHeader header = new AuthHeader(token);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            header.writeTo(out);
        }

        AuthHeader restored = new AuthHeader();
        ByteArrayInputStream input = new ByteArrayInputStream(bytes.toByteArray());
        try (DataInputStream in = new DataInputStream(input)) {
            restored.readFrom(in);
        }

        assertThat(restored.getToken()).isInstanceOf(RegexMembership.class);
        assertThat(restored.getToken().getName()).isEqualTo(RegexMembership.class.getName());
    }

    private static void configureJGroupsLoopbackDefaults() {
        System.setProperty("jgroups.bind_addr", "127.0.0.1");
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("jgroups.use.jdk_logger", "true");
        System.clearProperty("jgroups.log_class");
    }
}
