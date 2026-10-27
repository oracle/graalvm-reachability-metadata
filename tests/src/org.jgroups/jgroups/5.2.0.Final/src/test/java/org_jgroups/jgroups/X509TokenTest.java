/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.auth.X509Token;
import org.jgroups.stack.Configurator;
import org.jgroups.util.StackType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class X509TokenTest {
    private static final String KEYSTORE_RESOURCE = "org_jgroups/jgroups/x509-token-keystore.p12";
    private static final String KEYSTORE_PASSWORD = "changeit";
    private static final String CERTIFICATE_ALIAS = "jgroups-x509";
    private static final String AUTH_VALUE = "shared-jgroups-secret";

    static {
        configureJGroupsLoopbackDefaults();
    }

    @BeforeAll
    static void configureLoopbackDefaults() {
        configureJGroupsLoopbackDefaults();
    }

    @Test
    void loadsCertificateFromClasspathResourceAndAuthenticatesMatchingToken() throws Exception {
        X509Token coordinatorToken = configuredToken();
        X509Token joinerToken = configuredToken();

        coordinatorToken.setCertificate();
        joinerToken.setCertificate();

        assertThat(coordinatorToken.size()).isPositive();
        assertThat(coordinatorToken.authenticate(joinerToken, null)).isTrue();
    }

    private static X509Token configuredToken() throws Exception {
        X509Token token = new X509Token();
        Map<String, String> properties = new HashMap<>();
        properties.put("keystore_type", "PKCS12");
        properties.put("keystore_path", KEYSTORE_RESOURCE);
        properties.put("keystore_password", KEYSTORE_PASSWORD);
        properties.put("cert_alias", CERTIFICATE_ALIAS);
        properties.put("auth_value", AUTH_VALUE);
        properties.put("cipher_type", "RSA");

        Configurator.initializeAttrs(token, properties, StackType.IPv4);

        assertThat(properties).isEmpty();
        return token;
    }

    private static void configureJGroupsLoopbackDefaults() {
        System.setProperty("jgroups.bind_addr", "127.0.0.1");
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("jgroups.use.jdk_logger", "true");
    }
}
