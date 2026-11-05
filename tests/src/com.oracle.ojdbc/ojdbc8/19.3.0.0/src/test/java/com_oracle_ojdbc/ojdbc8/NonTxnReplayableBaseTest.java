/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.ojdbc8;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;
import oracle.jdbc.proxy.ProxyFactory;
import oracle.jdbc.proxy._Proxy_;
import oracle.jdbc.replay.OracleDataSourceImpl;
import oracle.jdbc.replay.driver.NonTxnReplayableConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(OracleDatabase.class)
public class NonTxnReplayableBaseTest {
    @Test
    @Timeout(120)
    void replaysConnectionCallsAndQueriesAfterLosingThePhysicalConnection() throws Exception {
        OracleDataSourceImpl source = new OracleDataSourceImpl();
        source.setURL(OracleDatabase.url());
        Properties properties = OracleDatabase.properties();
        // The legacy replay protocol invokes the database's private continuity package.
        properties.setProperty("user", "system");
        source.setConnectionProperties(properties);
        ProxyFactory factory = ProxyFactory.createProxyFactory(NonTxnReplayableConnection.class);

        try (Connection delegate = source.getConnection();
                Connection connection = factory.proxyFor(delegate)) {
            NonTxnReplayableConnection replay = (NonTxnReplayableConnection) connection;
            replay.initialize(source, source.createConnectionBuilder());
            replay.setReplayInitiationTimeout(60);
            replay.beginRequest();
            try {
                assertThat(connection.getAutoCommit()).isTrue();
                delegate.close();
                assertThat(delegate.isClosed()).isTrue();

                try (Statement statement = connection.createStatement();
                        ResultSet rows = statement.executeQuery("select 42, 'delivery-ready' from dual")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isEqualTo(42);
                    assertThat(rows.getString(2)).isEqualTo("delivery-ready");
                    assertThat(rows.next()).isFalse();
                }
                assertThat(ProxyFactory.extractDelegate((_Proxy_) connection)).isNotSameAs(delegate);
                assertThat(connection.getAutoCommit()).isTrue();
                assertThat(connection.isClosed()).isFalse();
            } finally {
                replay.endRequest();
            }
        }
    }
}
