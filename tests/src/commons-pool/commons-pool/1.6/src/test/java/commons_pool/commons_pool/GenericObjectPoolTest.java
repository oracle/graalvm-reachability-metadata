/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_pool.commons_pool;

import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.pool.ObjectPool;
import org.apache.commons.pool.impl.GenericObjectPool;
import org.apache.commons.pool.impl.GenericObjectPoolFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class GenericObjectPoolTest {

    @Test
    void reusesReturnedObjectsInLifoOrder() throws Exception {
        PooledResourceFactory factory = new PooledResourceFactory("lifo");
        GenericObjectPool<PooledResource> pool = new GenericObjectPool<>(factory);

        PooledResource first = pool.borrowObject();
        PooledResource second = pool.borrowObject();
        assertThat(pool.getNumActive()).isEqualTo(2);
        pool.returnObject(first);
        pool.returnObject(second);
        assertThat(pool.getNumIdle()).isEqualTo(2);
        assertThat(pool.getNumActive()).isZero();

        assertThat(pool.borrowObject()).isSameAs(second);
        assertThat(pool.borrowObject()).isSameAs(first);
        assertThat(factory.created()).containsExactly(first, second);
        pool.close();
    }

    @Test
    void servesIdleObjectsInFifoOrderWhenLifoIsDisabled() throws Exception {
        PooledResourceFactory factory = new PooledResourceFactory("fifo");
        GenericObjectPool<PooledResource> pool = new GenericObjectPool<>(factory);
        pool.setLifo(false);

        PooledResource first = pool.borrowObject();
        PooledResource second = pool.borrowObject();
        pool.returnObject(first);
        pool.returnObject(second);

        assertThat(pool.getLifo()).isFalse();
        assertThat(pool.borrowObject()).isSameAs(first);
        assertThat(pool.borrowObject()).isSameAs(second);
        pool.close();
    }

    @Test
    void activatesBorrowedObjectsAndPassivatesReturnedOnes() throws Exception {
        GenericObjectPool<PooledResource> pool = new GenericObjectPool<>(new PooledResourceFactory("cycle"));

        PooledResource resource = pool.borrowObject();
        assertThat(resource.isActive()).isTrue();
        pool.returnObject(resource);
        assertThat(resource.isActive()).isFalse();

        assertThat(pool.borrowObject()).isSameAs(resource);
        assertThat(resource.isActive()).isTrue();
        assertThat(resource.getActivations()).isEqualTo(2);
        pool.close();
    }

    @Test
    void failsFastWhenExhaustedWithTheFailAction() throws Exception {
        GenericObjectPool<PooledResource> pool = new GenericObjectPool<>(new PooledResourceFactory("fail"),
                2, GenericObjectPool.WHEN_EXHAUSTED_FAIL, 0L);

        PooledResource first = pool.borrowObject();
        pool.borrowObject();
        assertThatThrownBy(pool::borrowObject)
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("Pool exhausted");

        pool.returnObject(first);
        assertThat(pool.borrowObject()).isSameAs(first);
        pool.close();
    }

    @Test
    void growsBeyondMaxActiveWithTheGrowAction() throws Exception {
        PooledResourceFactory factory = new PooledResourceFactory("grow");
        GenericObjectPool<PooledResource> pool = new GenericObjectPool<>(factory,
                1, GenericObjectPool.WHEN_EXHAUSTED_GROW, 0L);

        PooledResource first = pool.borrowObject();
        PooledResource second = pool.borrowObject();
        PooledResource third = pool.borrowObject();

        assertThat(pool.getMaxActive()).isEqualTo(1);
        assertThat(pool.getNumActive()).isEqualTo(3);
        assertThat(factory.created()).containsExactly(first, second, third);
        pool.close();
    }

    @Test
    void blockedBorrowerReceivesTheObjectReturnedByAnotherThread() throws Exception {
        GenericObjectPool<PooledResource> pool = new GenericObjectPool<>(new PooledResourceFactory("block"),
                1, GenericObjectPool.WHEN_EXHAUSTED_BLOCK, TimeUnit.SECONDS.toMillis(30));
        PooledResource held = pool.borrowObject();
        AtomicReference<PooledResource> received = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread borrower = new Thread(() -> {
            try {
                received.set(pool.borrowObject());
            } catch (Throwable t) {
                failure.set(t);
            }
        }, "blocked-borrower");
        borrower.start();
        Await.until("the borrower waits for an idle object",
                () -> borrower.getState() == Thread.State.TIMED_WAITING);
        assertThat(received.get()).isNull();

        pool.returnObject(held);
        borrower.join(TimeUnit.SECONDS.toMillis(30));

        assertThat(borrower.isAlive()).isFalse();
        assertThat(failure.get()).isNull();
        assertThat(received.get()).isSameAs(held);
        pool.close();
    }

    @Test
    void invalidatedObjectsAreDestroyedAndReplaced() throws Exception {
        PooledResourceFactory factory = new PooledResourceFactory("invalidate");
        GenericObjectPool<PooledResource> pool = new GenericObjectPool<>(factory);

        PooledResource broken = pool.borrowObject();
        pool.invalidateObject(broken);

        assertThat(broken.isDestroyed()).isTrue();
        assertThat(pool.getNumActive()).isZero();
        assertThat(pool.getNumIdle()).isZero();
        PooledResource replacement = pool.borrowObject();
        assertThat(replacement).isNotSameAs(broken);
        assertThat(factory.created()).containsExactly(broken, replacement);
        pool.close();
    }

    @Test
    void testOnBorrowReplacesIdleObjectsThatFailValidation() throws Exception {
        PooledResourceFactory factory = new PooledResourceFactory("borrow-check");
        GenericObjectPool<PooledResource> pool = new GenericObjectPool<>(factory);
        pool.setTestOnBorrow(true);

        PooledResource stale = pool.borrowObject();
        pool.returnObject(stale);
        stale.markUnhealthy();

        PooledResource fresh = pool.borrowObject();
        assertThat(fresh).isNotSameAs(stale);
        assertThat(fresh.isHealthy()).isTrue();
        assertThat(factory.destroyed()).containsExactly(stale);
        pool.close();
    }

    @Test
    void testOnBorrowReportsFactoriesThatCannotCreateValidObjects() throws Exception {
        PooledResourceFactory factory = new PooledResourceFactory("never-valid");
        factory.createUnhealthyResources();
        GenericObjectPool<PooledResource> pool = new GenericObjectPool<>(factory);
        pool.setTestOnBorrow(true);

        assertThatThrownBy(pool::borrowObject)
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Could not create a validated object");
        assertThat(factory.created()).hasSize(1);
        assertThat(factory.destroyed()).containsExactlyElementsOf(factory.created());
        assertThat(pool.getNumActive()).isZero();
        pool.close();
    }

    @Test
    void testOnReturnDestroysObjectsThatFailValidation() throws Exception {
        PooledResourceFactory factory = new PooledResourceFactory("return-check");
        GenericObjectPool<PooledResource> pool = new GenericObjectPool<>(factory);
        pool.setTestOnReturn(true);

        PooledResource resource = pool.borrowObject();
        resource.markUnhealthy();
        pool.returnObject(resource);

        assertThat(resource.isDestroyed()).isTrue();
        assertThat(pool.getNumIdle()).isZero();
        assertThat(pool.getNumActive()).isZero();
        pool.close();
    }

    @Test
    void maxIdleCapsIdleObjectsAndDestroysTheSurplus() throws Exception {
        PooledResourceFactory factory = new PooledResourceFactory("max-idle");
        GenericObjectPool.Config config = new GenericObjectPool.Config();
        config.maxIdle = 1;
        GenericObjectPool<PooledResource> pool = new GenericObjectPool<>(factory, config);

        PooledResource first = pool.borrowObject();
        PooledResource second = pool.borrowObject();
        PooledResource third = pool.borrowObject();
        pool.returnObject(first);
        pool.returnObject(second);
        pool.returnObject(third);

        assertThat(pool.getMaxIdle()).isEqualTo(1);
        assertThat(pool.getNumIdle()).isEqualTo(1);
        assertThat(factory.destroyed()).containsExactly(second, third);
        assertThat(pool.borrowObject()).isSameAs(first);
        pool.close();
    }

    @Test
    void evictRemovesObjectsIdleLongerThanTheMinimumEvictableTime() throws Exception {
        PooledResourceFactory factory = new PooledResourceFactory("evict-age");
        GenericObjectPool<PooledResource> pool = new GenericObjectPool<>(factory);
        pool.setMinEvictableIdleTimeMillis(1L);
        pool.setNumTestsPerEvictionRun(10);
        pool.addObject();
        pool.addObject();
        pool.addObject();

        Thread.sleep(50L);
        pool.evict();

        assertThat(pool.getNumIdle()).isZero();
        assertThat(factory.destroyed()).containsExactlyInAnyOrderElementsOf(factory.created());
        pool.close();
    }

    @Test
    void evictWithTestWhileIdleRemovesOnlyInvalidObjects() throws Exception {
        PooledResourceFactory factory = new PooledResourceFactory("evict-test");
        GenericObjectPool<PooledResource> pool = new GenericObjectPool<>(factory);
        pool.setTestWhileIdle(true);
        pool.setNumTestsPerEvictionRun(10);
        PooledResource healthy = pool.borrowObject();
        PooledResource broken = pool.borrowObject();
        pool.returnObject(healthy);
        pool.returnObject(broken);
        broken.markUnhealthy();

        pool.evict();

        assertThat(pool.getNumIdle()).isEqualTo(1);
        assertThat(factory.destroyed()).containsExactly(broken);
        assertThat(healthy.isActive()).isFalse();
        assertThat(pool.borrowObject()).isSameAs(healthy);
        pool.close();
    }

    @Test
    void evictorThreadKeepsTheMinimumNumberOfIdleObjects() throws Exception {
        GenericObjectPool.Config config = new GenericObjectPool.Config();
        config.minIdle = 2;
        config.timeBetweenEvictionRunsMillis = 50L;
        GenericObjectPool<PooledResource> pool = new GenericObjectPool<>(new PooledResourceFactory("min-idle"), config);

        assertThat(pool.getMinIdle()).isEqualTo(2);
        assertThat(pool.getTimeBetweenEvictionRunsMillis()).isEqualTo(50L);
        Await.until("the evictor creates two idle objects", () -> pool.getNumIdle() == 2);
        assertThat(pool.getNumActive()).isZero();
        pool.close();
    }

    @Test
    void addObjectPrefillsIdleObjectsThatBorrowersReceive() throws Exception {
        PooledResourceFactory factory = new PooledResourceFactory("prefill");
        GenericObjectPool<PooledResource> pool = new GenericObjectPool<>(factory);
        pool.addObject();
        pool.addObject();

        assertThat(pool.getNumIdle()).isEqualTo(2);
        PooledResource borrowed = pool.borrowObject();
        assertThat(factory.created()).hasSize(2).contains(borrowed);
        assertThat(pool.getNumIdle()).isEqualTo(1);
        pool.close();
    }

    @Test
    void clearDestroysIdleObjectsOnly() throws Exception {
        PooledResourceFactory factory = new PooledResourceFactory("clear");
        GenericObjectPool<PooledResource> pool = new GenericObjectPool<>(factory);
        PooledResource active = pool.borrowObject();
        PooledResource idle = pool.borrowObject();
        pool.returnObject(idle);

        pool.clear();

        assertThat(pool.getNumIdle()).isZero();
        assertThat(pool.getNumActive()).isEqualTo(1);
        assertThat(factory.destroyed()).containsExactly(idle);
        assertThat(active.isDestroyed()).isFalse();
        pool.close();
    }

    @Test
    void closedPoolDestroysObjectsAndRejectsBorrowers() throws Exception {
        PooledResourceFactory factory = new PooledResourceFactory("closed");
        GenericObjectPool<PooledResource> pool = new GenericObjectPool<>(factory);
        PooledResource outstanding = pool.borrowObject();
        pool.addObject();

        pool.close();

        assertThat(factory.destroyed()).hasSize(1).doesNotContain(outstanding);
        assertThatThrownBy(pool::borrowObject).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(pool::addObject).isInstanceOf(IllegalStateException.class);
        pool.returnObject(outstanding);
        assertThat(outstanding.isDestroyed()).isTrue();
        assertThat(pool.getNumIdle()).isZero();
    }

    @Test
    void poolFactoryCreatesPoolsWithTheConfiguredLimits() throws Exception {
        GenericObjectPool.Config config = new GenericObjectPool.Config();
        config.maxActive = 2;
        config.maxIdle = 1;
        config.whenExhaustedAction = GenericObjectPool.WHEN_EXHAUSTED_FAIL;
        config.testOnReturn = true;
        config.lifo = false;
        PooledResourceFactory objectFactory = new PooledResourceFactory("factory");
        GenericObjectPoolFactory<PooledResource> poolFactory = new GenericObjectPoolFactory<>(objectFactory, config);

        assertThat(poolFactory.getFactory()).isSameAs(objectFactory);
        assertThat(poolFactory.getMaxActive()).isEqualTo(2);
        assertThat(poolFactory.getMaxIdle()).isEqualTo(1);
        assertThat(poolFactory.getWhenExhaustedAction()).isEqualTo(GenericObjectPool.WHEN_EXHAUSTED_FAIL);
        assertThat(poolFactory.getTestOnReturn()).isTrue();
        assertThat(poolFactory.getLifo()).isFalse();

        ObjectPool<PooledResource> pool = poolFactory.createPool();
        assertThat(pool).isInstanceOf(GenericObjectPool.class);
        PooledResource first = pool.borrowObject();
        PooledResource second = pool.borrowObject();
        assertThatThrownBy(pool::borrowObject).isInstanceOf(NoSuchElementException.class);
        pool.returnObject(first);
        pool.returnObject(second);
        assertThat(pool.getNumIdle()).isEqualTo(1);
        assertThat(objectFactory.destroyed()).containsExactly(second);
        pool.close();
    }
}
