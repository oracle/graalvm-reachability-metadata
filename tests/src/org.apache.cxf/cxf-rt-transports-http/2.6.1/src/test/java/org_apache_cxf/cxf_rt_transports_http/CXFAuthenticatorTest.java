/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_cxf.cxf_rt_transports_http;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.Authenticator;
import java.net.InetAddress;
import java.net.PasswordAuthentication;
import java.net.URL;

import org.apache.cxf.transport.http.CXFAuthenticator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class CXFAuthenticatorTest {
    @AfterEach
    void clearDefaultAuthenticator() {
        Authenticator.setDefault(null);
    }

    @Test
    void leavesUnrelatedServerAuthenticationUnanswered() throws Exception {
        CXFAuthenticator authenticator = new CXFAuthenticator();
        Authenticator.setDefault(authenticator);

        PasswordAuthentication authentication =
                Authenticator.requestPasswordAuthentication(
                        "localhost",
                        InetAddress.getLoopbackAddress(),
                        8080,
                        "http",
                        "test realm",
                        "basic",
                        new URL("http://localhost:8080/"),
                        Authenticator.RequestorType.SERVER);

        assertThat(authentication).isNull();
    }
}
