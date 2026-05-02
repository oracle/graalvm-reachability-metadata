/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb.mongo_java_driver;

import com.mongodb.internal.connection.SslHelper;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLParameters;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class SslHelperTest {
    @Test
    void enablesHostNameVerificationForSslParameters() {
        final SSLParameters sslParameters = new SSLParameters();

        SslHelper.enableHostNameVerification(sslParameters);

        assertThat(sslParameters.getEndpointIdentificationAlgorithm()).isEqualTo("HTTPS");
    }

    @Test
    void enablesServerNameIndicationForHostNames() {
        final SSLParameters sslParameters = new SSLParameters();
        final String host = "example.mongodb.local";

        SslHelper.enableSni(host, sslParameters);

        final List<SNIServerName> serverNames = sslParameters.getServerNames();
        assertThat(serverNames).hasSize(1);
        assertThat(serverNames.get(0)).isInstanceOf(SNIHostName.class);
        final SNIHostName serverName = (SNIHostName) serverNames.get(0);
        assertThat(serverName.getAsciiName()).isEqualTo(host);
    }
}
