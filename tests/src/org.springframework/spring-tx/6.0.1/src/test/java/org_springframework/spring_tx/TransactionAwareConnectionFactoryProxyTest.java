/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_tx;

import javax.naming.NamingException;
import javax.naming.Reference;
import javax.resource.ResourceException;
import javax.resource.cci.Connection;
import javax.resource.cci.ConnectionFactory;
import javax.resource.cci.ConnectionMetaData;
import javax.resource.cci.ConnectionSpec;
import javax.resource.cci.Interaction;
import javax.resource.cci.LocalTransaction;
import javax.resource.cci.RecordFactory;
import javax.resource.cci.ResourceAdapterMetaData;
import javax.resource.cci.ResultSetInfo;

import org.junit.jupiter.api.Test;

import org.springframework.jca.cci.connection.TransactionAwareConnectionFactoryProxy;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
public class TransactionAwareConnectionFactoryProxyTest {

    @Test
    void getConnectionReturnsTransactionAwareConnectionProxy() throws ResourceException {
        TestConnection targetConnection = new TestConnection();
        TestConnectionFactory targetConnectionFactory = new TestConnectionFactory(targetConnection);
        TransactionAwareConnectionFactoryProxy connectionFactory =
                new TransactionAwareConnectionFactoryProxy(targetConnectionFactory);

        Connection connection = connectionFactory.getConnection();
        ConnectionMetaData metadata = connection.getMetaData();
        connection.close();

        assertThat(metadata).isSameAs(targetConnection.metadata);
        assertThat(targetConnectionFactory.getConnectionCalls).isEqualTo(1);
        assertThat(targetConnection.getMetaDataCalls).isEqualTo(1);
        assertThat(targetConnection.closeCalls).isEqualTo(1);
        assertThat(connection).isNotSameAs(targetConnection);
    }

    private static final class TestConnectionFactory implements ConnectionFactory {

        private final Connection connection;

        private int getConnectionCalls;

        private TestConnectionFactory(Connection connection) {
            this.connection = connection;
        }

        @Override
        public Connection getConnection() {
            this.getConnectionCalls++;
            return this.connection;
        }

        @Override
        public Connection getConnection(ConnectionSpec properties) throws ResourceException {
            throw new ResourceException("Not used by this test");
        }

        @Override
        public RecordFactory getRecordFactory() throws ResourceException {
            throw new ResourceException("Not used by this test");
        }

        @Override
        public ResourceAdapterMetaData getMetaData() throws ResourceException {
            throw new ResourceException("Not used by this test");
        }

        @Override
        public Reference getReference() throws NamingException {
            throw new NamingException("Not used by this test");
        }

        @Override
        public void setReference(Reference reference) {
            throw new UnsupportedOperationException("Not used by this test");
        }
    }

    private static final class TestConnection implements Connection {

        private final ConnectionMetaData metadata = new TestConnectionMetaData();

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
            return this.metadata;
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

    private static final class TestConnectionMetaData implements ConnectionMetaData {

        @Override
        public String getEISProductName() {
            return "Test EIS";
        }

        @Override
        public String getEISProductVersion() {
            return "test";
        }

        @Override
        public String getUserName() {
            return "test";
        }
    }
}
