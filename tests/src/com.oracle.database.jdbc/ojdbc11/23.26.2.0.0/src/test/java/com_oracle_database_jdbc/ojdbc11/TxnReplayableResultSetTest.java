/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc11;

import static org.assertj.core.api.Assertions.assertThat;

import oracle.jdbc.internal.OracleResultSet;
import oracle.jdbc.proxy.ProxyFactory;
import oracle.jdbc.replay.driver.TxnReplayableResultSet;
import org.junit.jupiter.api.Test;

public class TxnReplayableResultSetTest {
    @Test
    void createsAReplayProxyForTheInternalResultSetContract() {
        ProxyFactory factory = ProxyFactory.createProxyFactory(TxnReplayableResultSet.class);

        Object proxy = factory.proxyForType(OracleResultSet.class);

        assertThat(proxy).isInstanceOf(TxnReplayableResultSet.class).isInstanceOf(OracleResultSet.class);
    }
}
