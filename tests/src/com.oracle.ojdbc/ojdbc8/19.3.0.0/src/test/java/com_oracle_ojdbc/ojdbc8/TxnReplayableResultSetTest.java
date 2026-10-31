/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.ojdbc8;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Date;
import java.sql.ResultSet;
import java.util.Map;
import oracle.jdbc.proxy.ProxyFactory;
import oracle.jdbc.proxy._Proxy_;
import oracle.jdbc.replay.driver.TxnReplayableResultSet;
import oracle.sql.ARRAY;
import org.junit.jupiter.api.Test;

public class TxnReplayableResultSetTest {
    @Test
    void cachesAnArrayResultSetProxyAndPreservesItsDelegate() throws Exception {
        OfflineConnection connection = new OfflineConnection();
        Date first = Date.valueOf("2024-04-12");
        Date second = Date.valueOf("2024-05-18");
        ARRAY array = new ARRAY(connection.arrayDescriptor(), connection, new Date[] {first, second});
        ProxyFactory factory = ProxyFactory.createProxyFactory(TxnReplayableResultSet.class);
        try (ResultSet delegate = array.getResultSet(Map.of())) {
            ResultSet proxy = factory.proxyForCache(delegate, factory, null, null);

            assertThat(proxy).isNotSameAs(delegate);
            assertThat(proxy).isInstanceOf(TxnReplayableResultSet.class);
            assertThat(factory.proxyForCache(delegate, factory, null, null)).isSameAs(proxy);
            ResultSet extracted = (ResultSet) ProxyFactory.extractDelegate((_Proxy_) proxy);
            assertThat(extracted).isSameAs(delegate);
            assertThat(extracted.next()).isTrue();
            assertThat(extracted.getInt(1)).isEqualTo(1);
            assertThat(extracted.getDate(2)).isEqualTo(first);
            assertThat(extracted.next()).isTrue();
            assertThat(extracted.getInt(1)).isEqualTo(2);
            assertThat(extracted.getDate(2)).isEqualTo(second);
            assertThat(extracted.next()).isFalse();
        } finally {
            array.free();
        }
    }
}
