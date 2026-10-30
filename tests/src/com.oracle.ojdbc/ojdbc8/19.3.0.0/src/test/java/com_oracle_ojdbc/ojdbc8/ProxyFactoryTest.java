/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.ojdbc8;

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
    void usesPreGeneratedReplayProxiesForCacheAndCreatePolicies() throws Exception {
        ProxyFactory factory = ProxyFactory.createProxyFactory(TxnReplayableOpaque.class);
        OpaqueDescriptor descriptor = OpaqueDescriptor.createDescriptor("SYS.ANYTYPE", null);
        OPAQUE first = new OPAQUE(descriptor, null, new byte[] {14, 28});
        OPAQUE second = new OPAQUE(descriptor, null, new byte[] {21, 42});

        OracleOpaque cached = factory.<OracleOpaque>proxyFor(first);
        assertThat(factory.<OracleOpaque>proxyFor(first)).isSameAs(cached);
        assertThat(ProxyFactory.extractDelegate((_Proxy_) cached)).isSameAs(first);

        OracleOpaque created = factory.<OracleOpaque>proxyForCreate(second, factory, null, null);
        OracleOpaque another = factory.<OracleOpaque>proxyForCreate(second, factory, null, null);
        assertThat(created).isNotSameAs(another);
        assertThat(ProxyFactory.extractDelegate((_Proxy_) created)).isSameAs(second);
        assertThat(ProxyFactory.extractDelegate((_Proxy_) another)).isSameAs(second);

        OracleOpaque createCached = factory.<OracleOpaque>proxyForCreateCache(second, factory, null, null);
        assertThat(factory.<OracleOpaque>proxyForCache(second, factory, null, null)).isSameAs(createCached);
        assertThat(ProxyFactory.extractDelegate((_Proxy_) createCached)).isSameAs(second);
        assertThat(first.getBytesValue()).containsExactly(14, 28);
        assertThat(second.getBytesValue()).containsExactly(21, 42);
    }
}
