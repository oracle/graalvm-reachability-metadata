/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc11;

import static org.assertj.core.api.Assertions.assertThat;

import oracle.jdbc.internal.OracleRef;
import oracle.jdbc.proxy.ProxyFactory;
import oracle.jdbc.replay.driver.TxnReplayableRef;
import org.junit.jupiter.api.Test;

public class TxnReplayableRefTest {
    @Test
    void createsAReplayProxyForTheInternalRefContract() {
        ProxyFactory factory = ProxyFactory.createProxyFactory(TxnReplayableRef.class);

        Object proxy = factory.proxyForType(OracleRef.class);

        assertThat(proxy).isInstanceOf(TxnReplayableRef.class).isInstanceOf(OracleRef.class);
    }
}
