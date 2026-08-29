/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc11;

import static org.assertj.core.api.Assertions.assertThat;

import oracle.jdbc.internal.OracleConnection;
import oracle.jdbc.internal.OracleOpaque;
import oracle.jdbc.internal.OracleResultSet;
import oracle.jdbc.internal.OracleStatement;
import oracle.jdbc.proxy.ProxyFactory;
import oracle.jdbc.replay.driver.TxnReplayableConnection;
import oracle.jdbc.replay.driver.TxnReplayableOpaque;
import oracle.jdbc.replay.driver.TxnReplayableResultSet;
import oracle.jdbc.replay.driver.TxnReplayableStatement;
import org.junit.jupiter.api.Test;

public class AnnotationsRegistryInnerValueTest {
    @Test
    void registersAnnotatedReplayProxyDeclarations() {
        ProxyFactory factory = ProxyFactory.createProxyFactory(
                TxnReplayableConnection.class,
                TxnReplayableStatement.class,
                TxnReplayableResultSet.class,
                TxnReplayableOpaque.class);

        assertThat(factory.isProxied(OracleConnection.class)).isTrue();
        assertThat(factory.isProxied(OracleStatement.class)).isTrue();
        assertThat(factory.isProxied(OracleResultSet.class)).isTrue();
        assertThat(factory.isProxied(OracleOpaque.class)).isTrue();
        assertThat(factory.isProxied(String.class)).isFalse();
    }
}
