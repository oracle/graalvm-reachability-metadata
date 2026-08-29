/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc11;

import static org.assertj.core.api.Assertions.assertThat;

import oracle.jdbc.internal.OracleStatement;
import oracle.jdbc.proxy.ProxyFactory;
import oracle.jdbc.replay.driver.TxnReplayableStatement;
import org.junit.jupiter.api.Test;

public class TxnReplayableStatementTest {
    @Test
    void createsAReplayProxyForTheInternalStatementContract() {
        ProxyFactory factory = ProxyFactory.createProxyFactory(TxnReplayableStatement.class);

        Object proxy = factory.proxyForType(OracleStatement.class);

        assertThat(proxy).isInstanceOf(TxnReplayableStatement.class).isInstanceOf(OracleStatement.class);
    }
}
