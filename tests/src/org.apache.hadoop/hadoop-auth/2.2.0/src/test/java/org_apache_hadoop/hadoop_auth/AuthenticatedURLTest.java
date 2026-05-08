/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_auth;

import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.hadoop.security.authentication.client.AuthenticatedURL;
import org.apache.hadoop.security.authentication.client.Authenticator;
import org.apache.hadoop.security.authentication.client.KerberosAuthenticator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AuthenticatedURLTest {
    @Test
    void opensConnectionWithDefaultAuthenticatorWhenTokenIsAlreadySet() throws Exception {
        Class<? extends Authenticator> previousAuthenticator = AuthenticatedURL.getDefaultAuthenticator();
        AuthenticatedURL.setDefaultAuthenticator(KerberosAuthenticator.class);
        try {
            AuthenticatedURL.Token token = new AuthenticatedURL.Token("delegation-token");
            AuthenticatedURL authenticatedURL = new AuthenticatedURL();

            HttpURLConnection connection = authenticatedURL.openConnection(new URL("http://localhost/"), token);
            try {
                assertThat(connection.getRequestProperty("Cookie"))
                    .isEqualTo("hadoop.auth=\"delegation-token\"");
            } finally {
                connection.disconnect();
            }
        } finally {
            AuthenticatedURL.setDefaultAuthenticator(previousAuthenticator);
        }
    }
}
