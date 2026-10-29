/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_pool.commons_pool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.pool.KeyedObjectPool;
import org.apache.commons.pool.KeyedPoolableObjectFactory;
import org.apache.commons.pool.ObjectPool;
import org.apache.commons.pool.PoolUtils;
import org.apache.commons.pool.PoolableObjectFactory;
import org.apache.commons.pool.impl.GenericKeyedObjectPool;
import org.apache.commons.pool.impl.GenericObjectPool;
import org.apache.commons.pool.impl.StackObjectPool;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PoolUtilsTest {

    @Test
    void checkedPoolRejectsObjectsOfTheWrongType() throws Exception {
        GenericObjectPool<PooledResource> pool = new GenericObjectPool<>(new PooledResourceFactory("checked"));
        ObjectPool<PooledResource> checked = PoolUtils.checkedPool(pool, PooledResource.class);

        PooledResource resource = checked.borrowObject();
        checked.returnObject(resource);
        assertThat(pool.getNumIdle()).isEqualTo(1);

        ObjectPool<Object> uncheckedView = uncheckedView(checked);
        assertThatThrownBy(() -> uncheckedView.returnObject("not a pooled resource"))
                .isInstanceOf(ClassCastException.class)
                .hasMessageContaining(PooledResource.class.getName());
        assertThat(pool.getNumIdle()).isEqualTo(1);
        checked.close();
    }

    @Test
    void checkedKeyedPoolRejectsObjectsOfTheWrongType() throws Exception {
        GenericKeyedObjectPool<String, PooledResource> pool = new GenericKeyedObjectPool<>(
                new KeyedPooledResourceFactory());
        KeyedObjectPool<String, PooledResource> checked = PoolUtils.checkedPool(pool, PooledResource.class);

        PooledResource resource = checked.borrowObject("alpha");
        checked.returnObject("alpha", resource);
        assertThat(pool.getNumIdle("alpha")).isEqualTo(1);

        KeyedObjectPool<String, Object> uncheckedView = uncheckedView(checked);
        assertThatThrownBy(() -> uncheckedView.returnObject("alpha", 42))
                .isInstanceOf(ClassCastException.class);
        assertThat(pool.getNumIdle("alpha")).isEqualTo(1);
        checked.close();
    }

    @Test
    void synchronizedPoolServesConcurrentBorrowers() throws Exception {
        PooledResourceFactory factory = new PooledResourceFactory("synchronized");
        ObjectPool<PooledResource> pool = PoolUtils.synchronizedPool(
                new StackObjectPool<>(PoolUtils.synchronizedPoolableFactory(factory)));
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int worker = 0; worker < 4; worker++) {
                results.add(executor.submit(() -> {
                    int cycles = 0;
                    for (int i = 0; i < 200; i++) {
                        PooledResource resource = pool.borrowObject();
                        assertThat(resource.isActive()).isTrue();
                        pool.returnObject(resource);
                        cycles++;
                    }
                    return cycles;
                }));
            }
            for (Future<Integer> result : results) {
                assertThat(result.get(30, TimeUnit.SECONDS)).isEqualTo(200);
            }
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(pool.getNumActive()).isZero();
        assertThat(factory.created()).hasSizeBetween(1, 4);
        assertThat(pool.getNumIdle()).isEqualTo(factory.created().size());
        pool.close();
    }

    @Test
    void erodingPoolReturnsObjectsWhileTheShrinkIntervalHasNotElapsed() throws Exception {
        GenericObjectPool<PooledResource> pool = new GenericObjectPool<>(new PooledResourceFactory("steady"));
        ObjectPool<PooledResource> eroding = PoolUtils.erodingPool(pool);

        PooledResource first = eroding.borrowObject();
        PooledResource second = eroding.borrowObject();
        eroding.returnObject(first);
        eroding.returnObject(second);

        assertThat(eroding.getNumIdle()).isEqualTo(2);
        assertThat(first.isDestroyed()).isFalse();
        assertThat(second.isDestroyed()).isFalse();
        eroding.close();
    }

    @Test
    void erodingPoolDiscardsReturnedObjectsOnceTheShrinkIntervalElapses() throws Exception {
        PooledResourceFactory factory = new PooledResourceFactory("eroding");
        GenericObjectPool<PooledResource> pool = new GenericObjectPool<>(factory);
        PooledResource idle = pool.borrowObject();
        PooledResource returned = pool.borrowObject();
        pool.returnObject(idle);
        // A factor of 1e-5 scales the 15-minute shrink interval down to about 9 ms.
        ObjectPool<PooledResource> eroding = PoolUtils.erodingPool(pool, 0.00001f);

        Thread.sleep(100L);
        eroding.returnObject(returned);

        assertThat(returned.isDestroyed()).isTrue();
        assertThat(factory.destroyed()).containsExactly(returned);
        assertThat(pool.getNumIdle()).isEqualTo(1);
        assertThat(pool.getNumActive()).isZero();
        eroding.close();
    }

    @Test
    void prefillAddsIdleObjectsToPlainAndKeyedPools() throws Exception {
        GenericObjectPool<PooledResource> pool = new GenericObjectPool<>(new PooledResourceFactory("prefill"));
        PoolUtils.prefill(pool, 3);
        assertThat(pool.getNumIdle()).isEqualTo(3);
        pool.close();

        GenericKeyedObjectPool<String, PooledResource> keyedPool = new GenericKeyedObjectPool<>(
                new KeyedPooledResourceFactory());
        PoolUtils.prefill(keyedPool, Arrays.asList("alpha", "beta"), 2);
        PoolUtils.prefill(keyedPool, "gamma", 1);
        assertThat(keyedPool.getNumIdle("alpha")).isEqualTo(2);
        assertThat(keyedPool.getNumIdle("beta")).isEqualTo(2);
        assertThat(keyedPool.getNumIdle("gamma")).isEqualTo(1);
        keyedPool.close();
    }

    @Test
    void checkMinIdleTopsUpIdleObjectsPeriodically() throws Exception {
        GenericObjectPool<PooledResource> pool = new GenericObjectPool<>(new PooledResourceFactory("timer"));
        TimerTask task = PoolUtils.checkMinIdle(pool, 2, 50L);
        try {
            Await.until("the timer adds two idle objects", () -> pool.getNumIdle() == 2);
        } finally {
            task.cancel();
        }
        assertThat(pool.getNumActive()).isZero();
        pool.close();
    }

    @Test
    void checkMinIdleTopsUpIdleObjectsForAKey() throws Exception {
        GenericKeyedObjectPool<String, PooledResource> pool = new GenericKeyedObjectPool<>(
                new KeyedPooledResourceFactory());
        TimerTask task = PoolUtils.checkMinIdle(pool, "alpha", 1, 50L);
        try {
            Await.until("the timer adds an idle object for the key", () -> pool.getNumIdle("alpha") == 1);
        } finally {
            task.cancel();
        }
        assertThat(pool.getNumIdle("beta")).isZero();
        pool.close();
    }

    @Test
    void adaptsKeyedFactoriesToPlainPoolsForOneKey() throws Exception {
        PoolableObjectFactory<PooledResource> factory = PoolUtils.adapt(new KeyedPooledResourceFactory(), "alpha");
        GenericObjectPool<PooledResource> pool = new GenericObjectPool<>(factory);
        pool.setTestOnBorrow(true);

        PooledResource resource = pool.borrowObject();

        assertThat(resource.getName()).startsWith("alpha-");
        assertThat(resource.isActive()).isTrue();
        pool.returnObject(resource);
        assertThat(resource.isActive()).isFalse();
        pool.close();
    }

    @Test
    void adaptsPlainFactoriesAndKeyedPoolsInBothDirections() throws Exception {
        PooledResourceFactory factory = new PooledResourceFactory("adapted");
        KeyedPoolableObjectFactory<Object, PooledResource> keyedFactory = PoolUtils.adapt(factory);
        GenericKeyedObjectPool<Object, PooledResource> keyedPool = new GenericKeyedObjectPool<>(keyedFactory);
        ObjectPool<PooledResource> alphaView = PoolUtils.adapt(keyedPool, "alpha");

        PooledResource resource = alphaView.borrowObject();
        assertThat(keyedPool.getNumActive("alpha")).isEqualTo(1);
        alphaView.returnObject(resource);
        assertThat(keyedPool.getNumIdle("alpha")).isEqualTo(1);
        assertThat(keyedPool.getNumIdle("beta")).isZero();

        KeyedObjectPool<String, PooledResource> keyIgnoringView = PoolUtils.adapt(alphaView);
        assertThat(keyIgnoringView.borrowObject("any key")).isSameAs(resource);
        assertThat(factory.created()).containsExactly(resource);
        keyedPool.close();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ObjectPool<Object> uncheckedView(ObjectPool<?> pool) {
        return (ObjectPool) pool;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static KeyedObjectPool<String, Object> uncheckedView(KeyedObjectPool<String, ?> pool) {
        return (KeyedObjectPool) pool;
    }
}
