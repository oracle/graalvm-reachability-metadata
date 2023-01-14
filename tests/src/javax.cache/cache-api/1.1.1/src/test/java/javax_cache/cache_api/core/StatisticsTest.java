/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_cache.cache_api.core;

import org.junit.jupiter.api.Test;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.AccessedExpiryPolicy;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import java.util.ArrayList;
import java.util.stream.IntStream;

import static javax.cache.expiry.Duration.ONE_HOUR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StatisticsTest {
    private enum CacheStatistics {
        CacheHits, CacheHitPercentage,
        CacheMisses, CacheMissPercentage,
        CacheGets, CachePuts, CacheRemovals, CacheEvictions,
        AverageGetTime, AveragePutTime, AverageRemoveTime
    }

    @Test
    public void accessStatistics() throws MalformedObjectNameException, AttributeNotFoundException, MBeanException, ReflectionException,
            InstanceNotFoundException {
        CacheManager cacheManager = Caching.getCachingProvider().getCacheManager();
        MutableConfiguration<String, Integer> config = new MutableConfiguration<String, Integer>()
                .setTypes(String.class, Integer.class).setExpiryPolicyFactory(AccessedExpiryPolicy.factoryOf(ONE_HOUR)).setStatisticsEnabled(true);
        Cache<String, Integer> cache = cacheManager.createCache("simpleCacheWithStatistics", config);
        long cacheHits = 5;
        long cacheMisses = 3;
        long cacheGets = cacheHits + cacheMisses;
        long cacheRemovals = 3;
        long cachePuts = cacheRemovals + 1;
        long cacheEvictions = 0;
        float cacheHitPercentage = (float) cacheHits / cacheGets * 100.0f;
        float cacheMissPercentage = (float) cacheMisses / cacheGets * 100.0f;
        cache.put("valid-key", 1);
        IntStream.iterate(0, i -> i < cacheHits, i -> i + 1).mapToObj(i -> "valid-key").forEach(cache::get);
        IntStream.iterate(0, i -> i < cacheMisses, i -> i + 1).mapToObj(i -> "invalid-key").forEach(cache::get);
        IntStream.iterate(0, i -> i < cacheRemovals, i -> i + 1).forEach(i -> cache.put("key" + i, i));
        IntStream.iterate(0, i -> i < cacheRemovals, i -> i + 1).mapToObj(i -> "key" + i).forEach(cache::remove);
        ArrayList<MBeanServer> mBeanServers = MBeanServerFactory.findMBeanServer(null);
        ObjectName objectName = new ObjectName("javax.cache:type=CacheStatistics"
                + ",CacheManager=" + (cache.getCacheManager().getURI().toString())
                + ",Cache=" + cache.getName());
        assertEquals(cacheHits, mBeanServers.get(0).getAttribute(objectName, CacheStatistics.CacheHits.toString()));
        assertEquals(cacheHitPercentage, mBeanServers.get(0).getAttribute(objectName, CacheStatistics.CacheHitPercentage.toString()));
        assertEquals(cacheMisses, mBeanServers.get(0).getAttribute(objectName, CacheStatistics.CacheMisses.toString()));
        assertEquals(cacheMissPercentage, mBeanServers.get(0).getAttribute(objectName, CacheStatistics.CacheMissPercentage.toString()));
        assertEquals(cacheGets, mBeanServers.get(0).getAttribute(objectName, CacheStatistics.CacheGets.toString()));
        assertEquals(cachePuts, mBeanServers.get(0).getAttribute(objectName, CacheStatistics.CachePuts.toString()));
        assertEquals(cacheRemovals, mBeanServers.get(0).getAttribute(objectName, CacheStatistics.CacheRemovals.toString()));
        assertEquals(cacheEvictions, mBeanServers.get(0).getAttribute(objectName, CacheStatistics.CacheEvictions.toString()));
        assertTrue((float) mBeanServers.get(0).getAttribute(objectName, CacheStatistics.AverageGetTime.toString()) > 0.0f);
        assertTrue((float) mBeanServers.get(0).getAttribute(objectName, CacheStatistics.AveragePutTime.toString()) > 0.0f);
        assertTrue((float) mBeanServers.get(0).getAttribute(objectName, CacheStatistics.AverageRemoveTime.toString()) > 0.0f);
        for (CacheStatistics cacheStatistic : CacheStatistics.values()) {
            assertThat(mBeanServers.get(0).getAttribute(objectName, cacheStatistic.toString())).isNotNull();
        }
    }
}
