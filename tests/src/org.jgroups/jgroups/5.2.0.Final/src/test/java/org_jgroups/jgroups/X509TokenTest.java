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
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class X509TokenTest {
    private static final String KEYSTORE_RESOURCE = "org_jgroups/jgroups/x509-token-test.jks";
    private static final String KEYSTORE_PASSWORD = "changeit";
    private static final String CERTIFICATE_ALIAS = "jgroups-test";
    private static final String AUTH_VALUE = "shared-x509-auth-value";

    @Test
    void loadsCertificateFromClasspathResourceAndAuthenticatesMatchingToken() throws Exception {
        X509Token token = new X509Token();
        Map<String, String> properties = new HashMap<>(Map.of(
            "keystore_path", KEYSTORE_RESOURCE,
            "keystore_password", KEYSTORE_PASSWORD,
            "cert_password", KEYSTORE_PASSWORD,
            "cert_alias", CERTIFICATE_ALIAS,
            "auth_value", AUTH_VALUE,
            "cipher_type", "RSA"));

        Configurator.initializeAttrs(token, properties, StackType.IPv4);
        token.setCertificate();

        assertThat(properties).isEmpty();
        assertThat(token.size()).isPositive();
        assertThat(token.authenticate(token, null)).isTrue();
    }
}
