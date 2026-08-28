/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc11;

import static org.assertj.core.api.Assertions.assertThat;

import oracle.jdbc.internal.OracleOpaque;
import oracle.jdbc.proxy.ProxyFactory;
import oracle.jdbc.proxy._Proxy_;
import oracle.jdbc.replay.driver.TxnReplayableOpaque;
import oracle.sql.OPAQUE;
import oracle.sql.OpaqueDescriptor;
import org.junit.jupiter.api.Test;

public class ProxyFactoryTest {
    @Test
    void createsAndCachesPreGeneratedReplayProxies() throws Exception {
        ProxyFactory factory = ProxyFactory.createProxyFactory(TxnReplayableOpaque.class);
        OPAQUE cachedDelegate = opaque(1);

        OracleOpaque cachedProxy = factory.<OracleOpaque>proxyFor(cachedDelegate);
        assertThat(factory.<OracleOpaque>proxyFor(cachedDelegate)).isSameAs(cachedProxy);
        assertThat(delegateOf(cachedProxy)).isSameAs(cachedDelegate);
        assertThat(cachedProxy.getBytesValue()).containsExactly(1);

        OPAQUE createdDelegate = opaque(2);
        OracleOpaque createdProxy = factory.<OracleOpaque>proxyForCreate(createdDelegate);
        assertThat(delegateOf(createdProxy)).isSameAs(createdDelegate);

        OPAQUE createCachedDelegate = opaque(3);
        OracleOpaque createCachedProxy = factory.<OracleOpaque>proxyForCreateCache(
                createCachedDelegate, factory, null, null);
        assertThat(delegateOf(createCachedProxy)).isSameAs(createCachedDelegate);

        assertThat(factory.<OracleOpaque>proxyForType(OracleOpaque.class)).isNotNull();
        assertThat(factory.isProxied(OracleOpaque.class)).isTrue();
    }

    private static OPAQUE opaque(int value) throws Exception {
        OpaqueDescriptor descriptor = OpaqueDescriptor.createDescriptor("SYS.ANYTYPE", null);
        return new OPAQUE(descriptor, null, new byte[] {(byte) value});
    }

    @SuppressWarnings("unchecked")
    private static OracleOpaque delegateOf(OracleOpaque proxy) {
        return ProxyFactory.extractDelegate((_Proxy_<OracleOpaque>) proxy);
    }
}
