/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache_core;

import static org.assertj.core.api.Assertions.assertThat;

import net.sf.ehcache.Cache;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.Status;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.exceptionhandler.ExceptionHandlingDynamicCacheProxy;

import org.junit.jupiter.api.Test;

public class ExceptionHandlingDynamicCacheProxyTest {
    @Test
    void createsDynamicProxyThatDelegatesCacheOperations() {
        String cacheName = "dynamic-proxy-coverage";
        Cache cache = new Cache(new CacheConfiguration(cacheName, 4).eternal(true));
        Ehcache proxy = ExceptionHandlingDynamicCacheProxy.createProxy(cache);

        assertThat(proxy.getName()).isEqualTo(cacheName);

        try {
            proxy.initialise();
            proxy.put(new Element("covered-key", "covered-value"));

            assertThat(proxy.isKeyInCache("covered-key")).isTrue();
            assertThat(proxy.get("covered-key").getObjectValue()).isEqualTo("covered-value");
        } finally {
            if (cache.getStatus() == Status.STATUS_ALIVE) {
                proxy.dispose();
            }
        }
    }
}
