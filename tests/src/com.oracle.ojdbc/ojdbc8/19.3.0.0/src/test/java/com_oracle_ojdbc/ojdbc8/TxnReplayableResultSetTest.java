/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.ojdbc8;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.Statement;
import oracle.jdbc.proxy.ProxyFactory;
import oracle.jdbc.proxy._Proxy_;
import oracle.jdbc.replay.OracleDataSourceImpl;
import oracle.jdbc.replay.driver.TxnReplayableResultSet;
import oracle.jdbc.replay.internal.ReplayableConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(OracleDatabase.class)
public class TxnReplayableResultSetTest {
    @Test
    @Timeout(120)
    void readsDatesThroughAPreGeneratedReplayResultSet() throws Exception {
        OracleDataSourceImpl source = new OracleDataSourceImpl();
        source.setURL(OracleDatabase.url());
        source.setConnectionProperties(OracleDatabase.properties());
        ProxyFactory factory = source.getProxyFactory();
        try (Connection delegate = source.getConnection()) {
            Connection connection = factory.proxyFor(delegate);
            ReplayableConnection replay = (ReplayableConnection) connection;
            replay.initialize(source, source.createConnectionBuilder());
            try (Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery("""
                            select 1 as position, date '2024-04-12' as delivery_date from dual
                            union all
                            select 2, date '2024-05-18' from dual
                            order by position
                            """)) {
                assertThat(rows).isInstanceOf(TxnReplayableResultSet.class);
                ResultSet extracted = (ResultSet) ProxyFactory.extractDelegate((_Proxy_) rows);
                assertThat(extracted).isNotSameAs(rows);
                assertThat(factory.proxyForCache(extracted, statement, null, null)).isSameAs(rows);
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(1);
                assertThat(rows.getDate(2)).isEqualTo(Date.valueOf("2024-04-12"));
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(2);
                assertThat(rows.getDate(2)).isEqualTo(Date.valueOf("2024-05-18"));
                assertThat(rows.next()).isFalse();
            }
        }
    }
}
