/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client_jakarta;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;

import org.apache.activemq.ActiveMQSslConnectionFactory;
import org.junit.jupiter.api.Test;

public class ActiveMQSslConnectionFactoryTest {
    @Test
    void missingNonUrlPathFallsBackToContextClassLoaderResourceLookup() {
        ExposedActiveMQSslConnectionFactory factory = new ExposedActiveMQSslConnectionFactory();

        assertThatThrownBy(() -> factory.open("missing-activemq-ssl-resource-for-coverage"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("missing-activemq-ssl-resource-for-coverage");
    }

    private static final class ExposedActiveMQSslConnectionFactory extends ActiveMQSslConnectionFactory {
        InputStream open(String urlOrResource) throws IOException {
            return getInputStream(urlOrResource);
        }
    }
}
