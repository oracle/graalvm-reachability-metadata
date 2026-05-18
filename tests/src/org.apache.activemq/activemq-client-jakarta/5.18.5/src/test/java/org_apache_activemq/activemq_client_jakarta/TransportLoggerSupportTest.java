/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client_jakarta;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.activemq.TransportLoggerSupport;
import org.apache.activemq.transport.TransportLoggerFactorySPI;
import org.junit.jupiter.api.Test;

public class TransportLoggerSupportTest {
    @Test
    void initializesTransportLoggerFactorySpiFromBrokerClasspath() {
        assertThat(TransportLoggerSupport.spi).isInstanceOf(TransportLoggerFactorySPI.class);
    }
}
