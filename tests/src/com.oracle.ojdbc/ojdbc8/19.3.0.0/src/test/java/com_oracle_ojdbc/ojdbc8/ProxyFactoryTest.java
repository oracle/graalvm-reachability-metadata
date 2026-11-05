/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.ojdbc8;

import static org.assertj.core.api.Assertions.assertThat;

import oracle.jdbc.internal.OracleConnection;
import oracle.jdbc.internal.OracleOpaque;
import oracle.jdbc.proxy.ProxyFactory;
import oracle.jdbc.proxy._Proxy_;
import oracle.jdbc.replay.driver.TxnReplayableConnection;
import oracle.jdbc.replay.driver.TxnReplayableOpaque;
import oracle.sql.OPAQUE;
import oracle.sql.OpaqueDescriptor;
import org.junit.jupiter.api.Test;

public class ProxyFactoryTest {
    @Test
    void createsAndStoresTheFirstConnectionProxyWithTheCreateCachePolicy() throws Exception {
        ProxyFactory factory = ProxyFactory.createProxyFactory(TxnReplayableConnection.class);
        OfflineConnection delegate = new OfflineConnection();

        OracleConnection first = factory.<OracleConnection>proxyForCreateCache(delegate, factory, null, null);

        assertThat(first).isNotSameAs(delegate);
        assertThat(delegate.getACProxy()).isSameAs(first);
        assertThat(ProxyFactory.extractDelegate((_Proxy_) first)).isSameAs(delegate);
        assertThat(factory.<OracleConnection>proxyForCache(delegate, factory, null, null)).isSameAs(first);

        OracleConnection replacement = factory.<OracleConnection>proxyForCreateCache(delegate, factory, null, null);

        assertThat(replacement).isNotSameAs(first);
        assertThat(delegate.getACProxy()).isSameAs(replacement);
        assertThat(ProxyFactory.extractDelegate((_Proxy_) replacement)).isSameAs(delegate);
        assertThat(factory.<OracleConnection>proxyForCache(delegate, factory, null, null)).isSameAs(replacement);
    }

    @Test
    void reusesThePreparedConstructorForAdditionalCachedConnections() throws Exception {
        ProxyFactory factory = ProxyFactory.createProxyFactory(TxnReplayableConnection.class);
        OfflineConnection firstDelegate = new OfflineConnection();
        OfflineConnection secondDelegate = new OfflineConnection();
        OfflineConnection thirdDelegate = new OfflineConnection();
        OfflineConnection fourthDelegate = new OfflineConnection();

        OracleConnection first = factory.<OracleConnection>proxyForCache(firstDelegate, factory, null, null);
        OracleConnection second = factory.<OracleConnection>proxyForCache(secondDelegate, factory, null, null);
        OracleConnection third = factory.<OracleConnection>proxyForCreateCache(thirdDelegate, factory, null, null);
        OracleConnection fourth = factory.<OracleConnection>proxyForCreateCache(fourthDelegate, factory, null, null);

        assertThat(second).isNotSameAs(first);
        assertThat(fourth).isNotSameAs(third);
        assertThat(firstDelegate.getACProxy()).isSameAs(first);
        assertThat(secondDelegate.getACProxy()).isSameAs(second);
        assertThat(thirdDelegate.getACProxy()).isSameAs(third);
        assertThat(fourthDelegate.getACProxy()).isSameAs(fourth);
        assertThat(ProxyFactory.extractDelegate((_Proxy_) second)).isSameAs(secondDelegate);
        assertThat(ProxyFactory.extractDelegate((_Proxy_) third)).isSameAs(thirdDelegate);
        assertThat(ProxyFactory.extractDelegate((_Proxy_) fourth)).isSameAs(fourthDelegate);
        assertThat(factory.<OracleConnection>proxyForCache(secondDelegate, factory, null, null)).isSameAs(second);
        assertThat(factory.<OracleConnection>proxyForCache(thirdDelegate, factory, null, null)).isSameAs(third);
        assertThat(factory.<OracleConnection>proxyForCache(fourthDelegate, factory, null, null)).isSameAs(fourth);
    }

    @Test
    void createsAndCachesAProxyOnTheFirstCacheLookup() throws Exception {
        ProxyFactory factory = ProxyFactory.createProxyFactory(TxnReplayableOpaque.class);
        OpaqueDescriptor descriptor = OpaqueDescriptor.createDescriptor("SYS.ANYTYPE", null);
        OPAQUE value = new OPAQUE(descriptor, null, new byte[] {3, 9, 27});

        OracleOpaque proxy = factory.<OracleOpaque>proxyForCache(value, factory, null, null);

        assertThat(proxy).isNotSameAs(value);
        assertThat(ProxyFactory.extractDelegate((_Proxy_) proxy)).isSameAs(value);
        assertThat(factory.<OracleOpaque>proxyForCache(value, factory, null, null)).isSameAs(proxy);
        assertThat(value.getBytesValue()).containsExactly(3, 9, 27);
    }

    @Test
    void storesCachedConnectionProxiesOnTheirPhysicalDelegates() throws Exception {
        ProxyFactory factory = ProxyFactory.createProxyFactory(TxnReplayableConnection.class);
        OfflineConnection cachedDelegate = new OfflineConnection();
        OfflineConnection createdDelegate = new OfflineConnection();

        OracleConnection cached = factory.<OracleConnection>proxyForCache(cachedDelegate, factory, null, null);
        OracleConnection created = factory.<OracleConnection>proxyForCreateCache(createdDelegate, factory, null, null);

        assertThat(cachedDelegate.getACProxy()).isSameAs(cached);
        assertThat(createdDelegate.getACProxy()).isSameAs(created);
        assertThat(factory.<OracleConnection>proxyForCache(cachedDelegate, factory, null, null)).isSameAs(cached);
        assertThat(factory.<OracleConnection>proxyForCache(createdDelegate, factory, null, null)).isSameAs(created);
        assertThat(ProxyFactory.extractDelegate((_Proxy_) cached)).isSameAs(cachedDelegate);
        assertThat(ProxyFactory.extractDelegate((_Proxy_) created)).isSameAs(createdDelegate);
    }

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
