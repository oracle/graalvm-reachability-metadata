/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_tx;

import javax.resource.ResourceException;
import javax.resource.cci.Connection;
import javax.resource.cci.ConnectionMetaData;
import javax.resource.cci.Interaction;
import javax.resource.cci.LocalTransaction;
import javax.resource.cci.ResultSetInfo;

import org.junit.jupiter.api.Test;

import org.springframework.jca.cci.connection.SingleConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
public class SingleConnectionFactoryInnerCloseSuppressingInvocationHandlerTest {

    @Test
    void proxyDelegatesNonCloseMethodToTargetConnection() throws ResourceException {
        TestConnection targetConnection = new TestConnection();
        SingleConnectionFactory connectionFactory = new SingleConnectionFactory(targetConnection);

        Connection connection = connectionFactory.getConnection();
        ConnectionMetaData metadata = connection.getMetaData();
        connection.close();

        assertThat(metadata).isNull();
        assertThat(targetConnection.getMetaDataCalls).isEqualTo(1);
        assertThat(targetConnection.closeCalls).isZero();
    }

    private static final class TestConnection implements Connection {

        private int getMetaDataCalls;

        private int closeCalls;

        @Override
        public Interaction createInteraction() throws ResourceException {
            throw new ResourceException("Not used by this test");
        }

        @Override
        public LocalTransaction getLocalTransaction() throws ResourceException {
            throw new ResourceException("Not used by this test");
        }

        @Override
        public ConnectionMetaData getMetaData() {
            this.getMetaDataCalls++;
            return null;
        }

        @Override
        public ResultSetInfo getResultSetInfo() throws ResourceException {
            throw new ResourceException("Not used by this test");
        }

        @Override
        public void close() {
            this.closeCalls++;
        }
    }
}
