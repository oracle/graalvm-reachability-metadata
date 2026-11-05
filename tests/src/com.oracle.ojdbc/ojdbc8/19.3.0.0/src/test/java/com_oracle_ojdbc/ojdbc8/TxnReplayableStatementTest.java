/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.ojdbc8;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.Types;
import oracle.jdbc.proxy.ProxyFactory;
import oracle.jdbc.replay.OracleDataSourceImpl;
import oracle.jdbc.replay.driver.TxnReplayableStatement;
import oracle.jdbc.replay.internal.ReplayableConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(OracleDatabase.class)
public class TxnReplayableStatementTest {
    @Test
    @Timeout(120)
    void retrievesLobAndObjectOutputsThroughAPreGeneratedReplayStatement() throws Exception {
        OracleDataSourceImpl dataSource = new OracleDataSourceImpl();
        dataSource.setURL(OracleDatabase.url());
        dataSource.setConnectionProperties(OracleDatabase.properties());
        try (Connection delegate = dataSource.getConnection()) {
            ProxyFactory factory = dataSource.getProxyFactory();
            Connection connection = factory.proxyFor(delegate);
            ReplayableConnection replay = (ReplayableConnection) connection;
            replay.initialize(dataSource, dataSource.createConnectionBuilder());
            try (CallableStatement statement = connection.prepareCall(
                    "begin ? := to_clob('shipment ready'); ? := 42; end;")) {
                assertThat(statement).isInstanceOf(TxnReplayableStatement.class);
                statement.registerOutParameter(1, Types.CLOB);
                statement.registerOutParameter(2, Types.NUMERIC);
                assertThat(statement.execute()).isFalse();
                Clob document = statement.getClob(1);
                try {
                    assertThat(document.getSubString(1, (int) document.length())).isEqualTo("shipment ready");
                } finally {
                    document.free();
                }
                assertThat(statement.getObject(2)).isEqualTo(BigDecimal.valueOf(42));
            }
        }
    }
}
