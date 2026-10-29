/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_pool.commons_pool;

import java.util.NoSuchElementException;

import org.apache.commons.pool.KeyedObjectPool;
import org.apache.commons.pool.impl.StackKeyedObjectPool;
import org.apache.commons.pool.impl.StackKeyedObjectPoolFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class StackKeyedObjectPoolTest {

    @Test
    void keepsOneStackPerKey() throws Exception {
        KeyedPooledResourceFactory factory = new KeyedPooledResourceFactory();
        StackKeyedObjectPool<String, PooledResource> pool = new StackKeyedObjectPool<>(factory);

        PooledResource alphaFirst = pool.borrowObject("alpha");
        PooledResource alphaSecond = pool.borrowObject("alpha");
        PooledResource beta = pool.borrowObject("beta");
        assertThat(pool.getNumActive("alpha")).isEqualTo(2);
        assertThat(pool.getNumActive()).isEqualTo(3);

        pool.returnObject("alpha", alphaFirst);
        pool.returnObject("alpha", alphaSecond);
        pool.returnObject("beta", beta);
        assertThat(pool.getNumIdle("alpha")).isEqualTo(2);
        assertThat(pool.getNumIdle("beta")).isEqualTo(1);
        assertThat(pool.getNumIdle()).isEqualTo(3);
        assertThat(pool.getNumActive()).isZero();
        assertThat(pool.toString()).contains("|alpha|=2", "|beta|=1");

        assertThat(pool.borrowObject("alpha")).isSameAs(alphaSecond);
        assertThat(pool.borrowObject("beta")).isSameAs(beta);
        pool.close();
    }

    @Test
    void destroysTheStalestIdleObjectOfAFullKey() throws Exception {
        KeyedPooledResourceFactory factory = new KeyedPooledResourceFactory();
        StackKeyedObjectPool<String, PooledResource> pool = new StackKeyedObjectPool<>(factory, 1);

        PooledResource first = pool.borrowObject("alpha");
        PooledResource second = pool.borrowObject("alpha");
        pool.returnObject("alpha", first);
        pool.returnObject("alpha", second);

        assertThat(pool.getMaxSleeping()).isEqualTo(1);
        assertThat(pool.getNumIdle("alpha")).isEqualTo(1);
        assertThat(factory.destroyed()).containsExactly(first);
        assertThat(pool.borrowObject("alpha")).isSameAs(second);
        pool.close();
    }

    @Test
    void addObjectAndClearWorkPerKey() throws Exception {
        KeyedPooledResourceFactory factory = new KeyedPooledResourceFactory();
        StackKeyedObjectPool<String, PooledResource> pool = new StackKeyedObjectPool<>(factory);
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
    void invalidatedObjectsAreDestroyed() throws Exception {
        KeyedPooledResourceFactory factory = new KeyedPooledResourceFactory();
        StackKeyedObjectPool<String, PooledResource> pool = new StackKeyedObjectPool<>(factory);
        PooledResource resource = pool.borrowObject("alpha");

        pool.invalidateObject("alpha", resource);

        assertThat(resource.isDestroyed()).isTrue();
        assertThat(pool.getNumActive("alpha")).isZero();
        assertThat(pool.borrowObject("alpha")).isNotSameAs(resource);
        pool.close();
    }

    @Test
    void poolWithoutAFactoryCannotCreateObjects() throws Exception {
        StackKeyedObjectPool<String, String> pool = new StackKeyedObjectPool<>();

        assertThatThrownBy(() -> pool.borrowObject("alpha"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("without a factory");
        assertThatThrownBy(() -> pool.addObject("alpha"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot add objects without a factory.");
        assertThat(pool.getFactory()).isNull();
        pool.close();
    }

    @Test
    void closedPoolRejectsBorrowersAndDestroysReturns() throws Exception {
        KeyedPooledResourceFactory factory = new KeyedPooledResourceFactory();
        StackKeyedObjectPool<String, PooledResource> pool = new StackKeyedObjectPool<>(factory);
        PooledResource outstanding = pool.borrowObject("alpha");
        pool.addObject("beta");

        pool.close();

        assertThat(pool.getNumIdle()).isZero();
        assertThatThrownBy(() -> pool.borrowObject("alpha")).isInstanceOf(IllegalStateException.class);
        pool.returnObject("alpha", outstanding);
        assertThat(outstanding.isDestroyed()).isTrue();
        assertThat(factory.destroyed()).hasSize(2);
    }

    @Test
    void poolFactoryCreatesBoundedKeyedStackPools() throws Exception {
        KeyedPooledResourceFactory objectFactory = new KeyedPooledResourceFactory();
        StackKeyedObjectPoolFactory<String, PooledResource> poolFactory =
                new StackKeyedObjectPoolFactory<>(objectFactory, 1, 4);

        assertThat(poolFactory.getFactory()).isSameAs(objectFactory);
        assertThat(poolFactory.getMaxSleeping()).isEqualTo(1);
        assertThat(poolFactory.getInitialCapacity()).isEqualTo(4);

        KeyedObjectPool<String, PooledResource> pool = poolFactory.createPool();
        assertThat(pool).isInstanceOf(StackKeyedObjectPool.class);
        PooledResource first = pool.borrowObject("alpha");
        PooledResource second = pool.borrowObject("alpha");
        pool.returnObject("alpha", first);
        pool.returnObject("alpha", second);
        assertThat(pool.getNumIdle("alpha")).isEqualTo(1);
        assertThat(objectFactory.destroyed()).containsExactly(first);
        pool.close();
    }
}
