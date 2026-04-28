/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb.mongo_java_driver;

import com.mongodb.ServerAddress;
import com.mongodb.internal.connection.SslHelper;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLParameters;

import static org.assertj.core.api.Assertions.assertThat;

public class SslHelperTest {
    @Test
    void configuresSslParametersForMongoServerConnections() {
        SSLParameters sslParameters = new SSLParameters();

        SslHelper.enableHostNameVerification(sslParameters);
        SslHelper.enableSni(new ServerAddress("mongo.example.com", 27017), sslParameters);

        assertThat(sslParameters.getEndpointIdentificationAlgorithm()).isEqualTo("HTTPS");
        assertThat(sslParameters.getServerNames()).hasSize(1);
        SNIServerName serverName = sslParameters.getServerNames().get(0);
        assertThat(serverName).isInstanceOf(SNIHostName.class);
        assertThat(((SNIHostName) serverName).getAsciiName()).isEqualTo("mongo.example.com");
    }
}
