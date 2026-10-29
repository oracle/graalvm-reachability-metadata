/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_pool.commons_pool;

import java.util.NoSuchElementException;

import org.apache.commons.pool.KeyedObjectPool;
import org.apache.commons.pool.impl.GenericKeyedObjectPool;
import org.apache.commons.pool.impl.GenericKeyedObjectPoolFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class GenericKeyedObjectPoolTest {

    @Test
    void keepsSeparateIdleObjectsPerKey() throws Exception {
        KeyedPooledResourceFactory factory = new KeyedPooledResourceFactory();
        GenericKeyedObjectPool<String, PooledResource> pool = new GenericKeyedObjectPool<>(factory);

        PooledResource alpha = pool.borrowObject("alpha");
        PooledResource beta = pool.borrowObject("beta");
        assertThat(alpha.getName()).startsWith("alpha-");
        assertThat(beta.getName()).startsWith("beta-");
        assertThat(pool.getNumActive("alpha")).isEqualTo(1);
        assertThat(pool.getNumActive()).isEqualTo(2);

        pool.returnObject("alpha", alpha);
        pool.returnObject("beta", beta);
        assertThat(pool.getNumIdle("alpha")).isEqualTo(1);
        assertThat(pool.getNumIdle("beta")).isEqualTo(1);
        assertThat(pool.getNumIdle()).isEqualTo(2);
        assertThat(alpha.isActive()).isFalse();

        assertThat(pool.borrowObject("beta")).isSameAs(beta);
        assertThat(pool.borrowObject("alpha")).isSameAs(alpha);
        assertThat(factory.created()).containsExactly(alpha, beta);
        pool.close();
    }

    @Test
    void enforcesMaxActivePerKey() throws Exception {
        GenericKeyedObjectPool<String, PooledResource> pool = new GenericKeyedObjectPool<>(
                new KeyedPooledResourceFactory(), 1, GenericKeyedObjectPool.WHEN_EXHAUSTED_FAIL, 0L);

        PooledResource alpha = pool.borrowObject("alpha");
        assertThatThrownBy(() -> pool.borrowObject("alpha"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("Pool exhausted");
        PooledResource beta = pool.borrowObject("beta");

        assertThat(beta.getName()).startsWith("beta-");
        pool.returnObject("alpha", alpha);
        assertThat(pool.borrowObject("alpha")).isSameAs(alpha);
        pool.close();
    }

    @Test
    void maxTotalReclaimsIdleObjectsOfOtherKeys() throws Exception {
        KeyedPooledResourceFactory factory = new KeyedPooledResourceFactory();
        GenericKeyedObjectPool<String, PooledResource> pool = new GenericKeyedObjectPool<>(factory);
        pool.setMaxActive(2);
        pool.setMaxTotal(2);
        pool.setWhenExhaustedAction(GenericKeyedObjectPool.WHEN_EXHAUSTED_FAIL);

        PooledResource first = pool.borrowObject("alpha");
        pool.borrowObject("alpha");
        assertThatThrownBy(() -> pool.borrowObject("beta")).isInstanceOf(NoSuchElementException.class);

        pool.returnObject("alpha", first);
        PooledResource beta = pool.borrowObject("beta");

        assertThat(beta.getName()).startsWith("beta-");
        assertThat(factory.destroyed()).containsExactly(first);
        assertThat(pool.getNumIdle("alpha")).isZero();
        assertThat(pool.getNumActive()).isEqualTo(2);
        assertThat(pool.getMaxTotal()).isEqualTo(2);
        pool.close();
    }

    @Test
    void clearForOneKeyDestroysOnlyThatKeysIdleObjects() throws Exception {
        KeyedPooledResourceFactory factory = new KeyedPooledResourceFactory();
        GenericKeyedObjectPool<String, PooledResource> pool = new GenericKeyedObjectPool<>(factory);
        pool.addObject("alpha");
        pool.addObject("alpha");
        pool.addObject("beta");

        pool.clear("alpha");

        assertThat(pool.getNumIdle("alpha")).isZero();
        assertThat(pool.getNumIdle("beta")).isEqualTo(1);
        assertThat(factory.destroyed()).hasSize(2).allMatch(resource -> resource.getName().startsWith("alpha-"));

        pool.clear();
        assertThat(pool.getNumIdle()).isZero();
        assertThat(factory.destroyed()).hasSize(3);
        pool.close();
    }

    @Test
    void clearOldestDestroysTheLongestIdleObject() throws Exception {
        KeyedPooledResourceFactory factory = new KeyedPooledResourceFactory();
        GenericKeyedObjectPool<String, PooledResource> pool = new GenericKeyedObjectPool<>(factory);
        PooledResource oldest = pool.borrowObject("alpha");
        PooledResource newer = pool.borrowObject("beta");
        pool.returnObject("alpha", oldest);
        Thread.sleep(20L);
        pool.returnObject("beta", newer);

        pool.clearOldest();

        assertThat(factory.destroyed()).containsExactly(oldest);
        assertThat(pool.getNumIdle("alpha")).isZero();
        assertThat(pool.getNumIdle("beta")).isEqualTo(1);
        pool.close();
    }

    @Test
    void preparePoolCreatesTheMinimumIdleObjectsForAKey() throws Exception {
        GenericKeyedObjectPool<String, PooledResource> pool = new GenericKeyedObjectPool<>(
                new KeyedPooledResourceFactory());
        pool.setMinIdle(2);

        pool.preparePool("alpha", true);

        assertThat(pool.getMinIdle()).isEqualTo(2);
        assertThat(pool.getNumIdle("alpha")).isEqualTo(2);
        assertThat(pool.getNumIdle("beta")).isZero();
        pool.close();
    }

    @Test
    void evictorThreadKeepsTheMinimumIdleObjectsForPreparedKeys() throws Exception {
        GenericKeyedObjectPool.Config config = new GenericKeyedObjectPool.Config();
        config.minIdle = 1;
        config.timeBetweenEvictionRunsMillis = 50L;
        GenericKeyedObjectPool<String, PooledResource> pool = new GenericKeyedObjectPool<>(
                new KeyedPooledResourceFactory(), config);

        pool.preparePool("alpha", false);
        pool.preparePool("beta", false);

        Await.until("the evictor creates an idle object per key",
                () -> pool.getNumIdle("alpha") == 1 && pool.getNumIdle("beta") == 1);
        pool.close();
    }

    @Test
    void evictWithTestWhileIdleRemovesOnlyInvalidObjects() throws Exception {
        KeyedPooledResourceFactory factory = new KeyedPooledResourceFactory();
        GenericKeyedObjectPool<String, PooledResource> pool = new GenericKeyedObjectPool<>(factory);
        pool.setTestWhileIdle(true);
        pool.setNumTestsPerEvictionRun(10);
        PooledResource healthy = pool.borrowObject("alpha");
        PooledResource broken = pool.borrowObject("alpha");
        PooledResource other = pool.borrowObject("beta");
        pool.returnObject("alpha", healthy);
        pool.returnObject("alpha", broken);
        pool.returnObject("beta", other);
        broken.markUnhealthy();

        pool.evict();

        assertThat(factory.destroyed()).containsExactly(broken);
        assertThat(pool.getNumIdle("alpha")).isEqualTo(1);
        assertThat(pool.getNumIdle("beta")).isEqualTo(1);
        assertThat(pool.borrowObject("alpha")).isSameAs(healthy);
        pool.close();
    }

    @Test
    void evictRemovesObjectsIdleLongerThanTheMinimumEvictableTime() throws Exception {
        KeyedPooledResourceFactory factory = new KeyedPooledResourceFactory();
        GenericKeyedObjectPool<String, PooledResource> pool = new GenericKeyedObjectPool<>(factory);
        pool.setMinEvictableIdleTimeMillis(1L);
        pool.setNumTestsPerEvictionRun(10);
        pool.addObject("alpha");
        pool.addObject("beta");

        Thread.sleep(50L);
        pool.evict();

        assertThat(pool.getNumIdle()).isZero();
        assertThat(factory.destroyed()).containsExactlyInAnyOrderElementsOf(factory.created());
        pool.close();
    }

    @Test
    void invalidatedObjectsAreDestroyedAndNoLongerActive() throws Exception {
        KeyedPooledResourceFactory factory = new KeyedPooledResourceFactory();
        GenericKeyedObjectPool<String, PooledResource> pool = new GenericKeyedObjectPool<>(factory);

        PooledResource resource = pool.borrowObject("alpha");
        pool.invalidateObject("alpha", resource);

        assertThat(resource.isDestroyed()).isTrue();
        assertThat(pool.getNumActive("alpha")).isZero();
        assertThat(pool.borrowObject("alpha")).isNotSameAs(resource);
        pool.close();
    }

    @Test
    void closedPoolDestroysIdleObjectsAndRejectsBorrowers() throws Exception {
        KeyedPooledResourceFactory factory = new KeyedPooledResourceFactory();
        GenericKeyedObjectPool<String, PooledResource> pool = new GenericKeyedObjectPool<>(factory);
        pool.addObject("alpha");
        pool.addObject("beta");

        pool.close();

        assertThat(factory.destroyed()).containsExactlyInAnyOrderElementsOf(factory.created());
        assertThatThrownBy(() -> pool.borrowObject("alpha")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void poolFactoryCreatesPoolsWithTheConfiguredLimits() throws Exception {
        GenericKeyedObjectPool.Config config = new GenericKeyedObjectPool.Config();
        config.maxActive = 1;
        config.maxTotal = 3;
        config.whenExhaustedAction = GenericKeyedObjectPool.WHEN_EXHAUSTED_FAIL;
        KeyedPooledResourceFactory objectFactory = new KeyedPooledResourceFactory();
        GenericKeyedObjectPoolFactory<String, PooledResource> poolFactory =
                new GenericKeyedObjectPoolFactory<>(objectFactory, config);

        assertThat(poolFactory.getFactory()).isSameAs(objectFactory);
        assertThat(poolFactory.getMaxActive()).isEqualTo(1);
        assertThat(poolFactory.getMaxTotal()).isEqualTo(3);
        assertThat(poolFactory.getWhenExhaustedAction()).isEqualTo(GenericKeyedObjectPool.WHEN_EXHAUSTED_FAIL);

        KeyedObjectPool<String, PooledResource> pool = poolFactory.createPool();
        assertThat(pool).isInstanceOf(GenericKeyedObjectPool.class);
        pool.borrowObject("alpha");
        pool.borrowObject("beta");
        assertThatThrownBy(() -> pool.borrowObject("alpha")).isInstanceOf(NoSuchElementException.class);
        assertThat(pool.getNumActive()).isEqualTo(2);
        pool.close();
    }
}
