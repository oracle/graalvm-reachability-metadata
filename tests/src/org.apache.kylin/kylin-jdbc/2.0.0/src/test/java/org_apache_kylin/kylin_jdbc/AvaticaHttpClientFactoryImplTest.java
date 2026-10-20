/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import java.net.URL;
import java.util.Properties;

import org.apache.kylin.jdbc.shaded.org.apache.calcite.avatica.ConnectionConfigImpl;
import org.apache.kylin.jdbc.shaded.org.apache.calcite.avatica.remote.AvaticaCommonsHttpClientImpl;
import org.apache.kylin.jdbc.shaded.org.apache.calcite.avatica.remote.AvaticaHttpClient;
import org.apache.kylin.jdbc.shaded.org.apache.calcite.avatica.remote.AvaticaHttpClientFactoryImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AvaticaHttpClientFactoryImplTest {
    @Test
    void constructsTheConfiguredDefaultHttpClient() throws Exception {
        AvaticaHttpClient client = AvaticaHttpClientFactoryImpl.getInstance()
                .getClient(new URL("http://127.0.0.1:8080/avatica"), new ConnectionConfigImpl(new Properties()), null);

        assertThat(client).isInstanceOf(AvaticaCommonsHttpClientImpl.class);
    }
}
