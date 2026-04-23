/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.C3P0ProxyConnection;
import com.mchange.v2.c3p0.C3P0ProxyStatement;
import com.mchange.v2.c3p0.WrapperConnectionPoolDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.PooledConnection;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

public class C3P0PooledConnectionTest {
    @Test
    void exercisesTraditionalReflectiveProxyConnectionsAndStatements() throws Exception {
        WrapperConnectionPoolDataSource dataSource = C3p0TestSupport.newWrapperConnectionPoolDataSource("traditional-proxy", true);

        PooledConnection pooledConnection = dataSource.getPooledConnection();
        try {
            Connection connection = pooledConnection.getConnection();
            C3P0ProxyConnection proxyConnection = (C3P0ProxyConnection) connection;

            assertThat(connection.toString()).contains("C3P0ProxyConnection");
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            connection.setCatalog(connection.getCatalog());
            connection.setHoldability(connection.getHoldability());
            connection.getMetaData();
            assertThat(connection.getAutoCommit()).isTrue();

            Statement rawStatement = (Statement) proxyConnection.rawConnectionOperation(
                Connection.class.getMethod("createStatement"),
                C3P0ProxyConnection.RAW_CONNECTION,
                new Object[0]
            );
            assertThat(rawStatement).isInstanceOf(C3P0ProxyStatement.class);

            Statement statement = connection.createStatement();
            ((C3P0ProxyStatement) statement).rawStatementOperation(
                Statement.class.getMethod("getFetchSize"),
                C3P0ProxyStatement.RAW_STATEMENT,
                new Object[0]
            );

            PreparedStatement preparedStatement = connection.prepareStatement("SELECT 1");
            ((C3P0ProxyStatement) preparedStatement).rawStatementOperation(
                Statement.class.getMethod("getMaxRows"),
                C3P0ProxyStatement.RAW_STATEMENT,
                new Object[0]
            );

            CallableStatement callableStatement = connection.prepareCall("CALL 1");
            ((C3P0ProxyStatement) callableStatement).rawStatementOperation(
                Statement.class.getMethod("getUpdateCount"),
                C3P0ProxyStatement.RAW_STATEMENT,
                new Object[0]
            );

            ResultSet tables = connection.getMetaData().getTables(null, null, null, null);
            assertThat(tables).isNotNull();

            connection.close();
        } finally {
            pooledConnection.close();
        }
    }
}
