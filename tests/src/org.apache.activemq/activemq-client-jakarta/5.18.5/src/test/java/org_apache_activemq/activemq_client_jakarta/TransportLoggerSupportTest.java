/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client_jakarta;

import java.io.IOException;
import java.security.cert.X509Certificate;

import org.apache.activemq.TransportLoggerSupport;
import org.apache.activemq.transport.Transport;
import org.apache.activemq.transport.TransportSupport;
import org.apache.activemq.util.ServiceStopper;
import org.apache.activemq.wireformat.WireFormat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TransportLoggerSupportTest {

    @Test
    void createTransportLoggerWrapsTransportWhenLoggingSpiIsAvailable() throws IOException {
        Transport transport = new TestTransport();

        Transport logger = TransportLoggerSupport.createTransportLogger(transport);

        assertThat(logger).isNotSameAs(transport);
        assertThat(logger.narrow(TestTransport.class)).isSameAs(transport);
    }

    @Test
    void createConfiguredTransportLoggerWrapsTransportWhenLoggingSpiIsAvailable() throws IOException {
        Transport transport = new TestTransport();

        Transport logger = TransportLoggerSupport.createTransportLogger(
                transport,
                TransportLoggerSupport.defaultLogWriterName,
                false,
                true,
                TransportLoggerSupport.defaultJmxPort);

        assertThat(logger).isNotSameAs(transport);
        assertThat(logger.narrow(TestTransport.class)).isSameAs(transport);
    }

    private static final class TestTransport extends TransportSupport {
        private Object lastCommand;
        private X509Certificate[] peerCertificates;

        @Override
        public void oneway(Object command) {
            this.lastCommand = command;
        }

        @Override
        public String getRemoteAddress() {
            return "vm://transport-logger-support-test";
        }

        @Override
        public int getReceiveCounter() {
            return 0;
        }

        @Override
        public X509Certificate[] getPeerCertificates() {
            return peerCertificates;
        }

        @Override
        public void setPeerCertificates(X509Certificate[] certificates) {
            this.peerCertificates = certificates;
        }

        @Override
        public WireFormat getWireFormat() {
            return null;
        }

        @Override
        protected void doStart() {
            this.lastCommand = null;
        }

        @Override
        protected void doStop(ServiceStopper stopper) {
            this.lastCommand = null;
        }
    }
}
