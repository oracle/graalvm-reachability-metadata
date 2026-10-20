/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import java.net.URL;
import java.util.Properties;

import org.apache.kylin.jdbc.shaded.org.apache.calcite.avatica.BuiltInConnectionProperty;
import org.apache.kylin.jdbc.shaded.org.apache.calcite.avatica.ConnectionConfigImpl;
import org.apache.kylin.jdbc.shaded.org.apache.calcite.avatica.remote.AvaticaHttpClient;
import org.apache.kylin.jdbc.shaded.org.apache.calcite.avatica.remote.AvaticaHttpClientFactoryImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AvaticaHttpClientFactoryImplTest {
    @Test
    void constructsTheConfiguredHttpClient() throws Exception {
        URL endpoint = new URL("http://127.0.0.1:8080/avatica");
        Properties properties = new Properties();
        properties.setProperty(
                BuiltInConnectionProperty.HTTP_CLIENT_IMPL.camelName(), RecordingHttpClient.class.getName());

        AvaticaHttpClient client = AvaticaHttpClientFactoryImpl.getInstance()
                .getClient(endpoint, new ConnectionConfigImpl(properties), null);

        assertThat(client).isInstanceOf(RecordingHttpClient.class);
        assertThat(((RecordingHttpClient) client).endpoint).isEqualTo(endpoint);
        assertThat(client.send(new byte[] {1, 2})).containsExactly(2, 1);
    }

    public static class RecordingHttpClient implements AvaticaHttpClient {
        private final URL endpoint;

        public RecordingHttpClient(URL endpoint) {
            this.endpoint = endpoint;
        }

        @Override
        public byte[] send(byte[] request) {
            return new byte[] {request[1], request[0]};
        }
    }
}
