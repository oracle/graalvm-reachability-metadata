/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.pulsar.shade.io.netty.channel.DefaultEventLoop;
import org.apache.pulsar.shade.io.netty.resolver.dns.DefaultDnsCache;
import org.apache.pulsar.shade.io.netty.resolver.dns.DnsCacheEntry;
import org.junit.jupiter.api.Test;

public class DefaultDnsCacheTest {
    @Test
    void returnsAnIndependentCopyOfCachedFailures() {
        DefaultEventLoop eventLoop = new DefaultEventLoop();
        DefaultDnsCache cache = new DefaultDnsCache(0, 60, 60);
        try {
            IllegalStateException failure = new IllegalStateException("resolver unavailable");
            cache.cache("pulsar.invalid", null, failure, eventLoop);

            List<? extends DnsCacheEntry> entries = cache.get("pulsar.invalid", null);

            assertThat(entries).hasSize(1);
            assertThat(entries.get(0).cause())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("resolver unavailable")
                    .isNotSameAs(failure);
        } finally {
            cache.clear();
            eventLoop.shutdownGracefully(0, 10, TimeUnit.SECONDS).syncUninterruptibly();
        }
    }
}
