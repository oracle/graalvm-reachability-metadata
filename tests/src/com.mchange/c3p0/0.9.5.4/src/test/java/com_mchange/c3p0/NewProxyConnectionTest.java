/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.C3P0ProxyConnection;
import com.mchange.v2.c3p0.C3P0ProxyStatement;
import com.mchange.v2.c3p0.PoolBackedDataSource;
import org.junit.jupiter.api.Test;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

public class NewProxyConnectionTest {
    @Test
    void exercisesGeneratedProxyRawOperationsAndStatementCaching() throws Exception {
        PoolBackedDataSource dataSource = C3p0TestSupport.newPoolBackedDataSource("new-proxy", false, 8);

        try {
            try (Connection connection = dataSource.getConnection()) {
                C3P0ProxyConnection proxyConnection = (C3P0ProxyConnection) connection;

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
                statement.close();

                PreparedStatement preparedStatement = connection.prepareStatement("SELECT 1");
                ((C3P0ProxyStatement) preparedStatement).rawStatementOperation(
                    Statement.class.getMethod("getMaxRows"),
                    C3P0ProxyStatement.RAW_STATEMENT,
                    new Object[0]
                );
                preparedStatement.close();

                PreparedStatement cachedPreparedStatement = connection.prepareStatement("SELECT 1");
                cachedPreparedStatement.close();

                CallableStatement callableStatement = connection.prepareCall("CALL 1");
                ((C3P0ProxyStatement) callableStatement).rawStatementOperation(
                    Statement.class.getMethod("getUpdateCount"),
                    C3P0ProxyStatement.RAW_STATEMENT,
                    new Object[0]
                );
                callableStatement.close();
            }

            assertThat(dataSource.getNumConnections()).isGreaterThanOrEqualTo(1);
        } finally {
            dataSource.close();
        }
    }
}
