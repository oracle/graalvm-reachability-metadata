/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_pool.commons_pool;

import java.util.NoSuchElementException;

import org.apache.commons.pool.ObjectPool;
import org.apache.commons.pool.impl.StackObjectPool;
import org.apache.commons.pool.impl.StackObjectPoolFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class StackObjectPoolTest {

    @Test
    void returnsTheMostRecentlyReturnedObjectFirst() throws Exception {
        PooledResourceFactory factory = new PooledResourceFactory("stack");
        StackObjectPool<PooledResource> pool = new StackObjectPool<>(factory);

        PooledResource first = pool.borrowObject();
        PooledResource second = pool.borrowObject();
        pool.returnObject(first);
        pool.returnObject(second);

        assertThat(pool.getNumIdle()).isEqualTo(2);
        assertThat(pool.borrowObject()).isSameAs(second);
        assertThat(pool.borrowObject()).isSameAs(first);
        assertThat(pool.getNumActive()).isEqualTo(2);
        assertThat(factory.created()).containsExactly(first, second);
        pool.close();
    }

    @Test
    void destroysTheStalestIdleObjectWhenFull() throws Exception {
        PooledResourceFactory factory = new PooledResourceFactory("bounded");
        StackObjectPool<PooledResource> pool = new StackObjectPool<>(factory, 2);

        PooledResource first = pool.borrowObject();
        PooledResource second = pool.borrowObject();
        PooledResource third = pool.borrowObject();
        pool.returnObject(first);
        pool.returnObject(second);
        pool.returnObject(third);

        assertThat(pool.getMaxSleeping()).isEqualTo(2);
        assertThat(pool.getNumIdle()).isEqualTo(2);
        assertThat(factory.destroyed()).containsExactly(first);
        assertThat(pool.borrowObject()).isSameAs(third);
        assertThat(pool.borrowObject()).isSameAs(second);
        pool.close();
    }

    @Test
    void discardsIdleObjectsThatFailValidationOnBorrow() throws Exception {
        PooledResourceFactory factory = new PooledResourceFactory("validated");
        StackObjectPool<PooledResource> pool = new StackObjectPool<>(factory);
        PooledResource stale = pool.borrowObject();
        pool.returnObject(stale);
        stale.markUnhealthy();

        PooledResource fresh = pool.borrowObject();

        assertThat(fresh).isNotSameAs(stale);
        assertThat(factory.destroyed()).containsExactly(stale);
        pool.close();
    }

    @Test
    void destroysReturnedObjectsThatFailValidation() throws Exception {
        PooledResourceFactory factory = new PooledResourceFactory("returned");
        StackObjectPool<PooledResource> pool = new StackObjectPool<>(factory);
        PooledResource resource = pool.borrowObject();
        resource.markUnhealthy();

        pool.returnObject(resource);

        assertThat(resource.isDestroyed()).isTrue();
        assertThat(pool.getNumIdle()).isZero();
        assertThat(pool.getNumActive()).isZero();
        pool.close();
    }

    @Test
    void poolWithoutAFactoryLendsOnlyObjectsReturnedToIt() throws Exception {
        StackObjectPool<String> pool = new StackObjectPool<>();

        assertThatThrownBy(pool::borrowObject).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(pool::addObject)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot add objects without a factory.");

        pool.returnObject("seeded");
        assertThat(pool.getNumIdle()).isEqualTo(1);
        assertThat(pool.borrowObject()).isEqualTo("seeded");
        assertThat(pool.getFactory()).isNull();
        pool.close();
    }

    @Test
    void addObjectAndClearManageIdleObjects() throws Exception {
        PooledResourceFactory factory = new PooledResourceFactory("idle");
        StackObjectPool<PooledResource> pool = new StackObjectPool<>(factory);
        pool.addObject();
        pool.addObject();
        assertThat(pool.getNumIdle()).isEqualTo(2);
        assertThat(factory.created()).allMatch(resource -> !resource.isActive());

        pool.clear();

        assertThat(pool.getNumIdle()).isZero();
        assertThat(factory.destroyed()).containsExactlyInAnyOrderElementsOf(factory.created());
        pool.close();
    }

    @Test
    void closedPoolRejectsBorrowersAndDestroysReturns() throws Exception {
        PooledResourceFactory factory = new PooledResourceFactory("closed");
        StackObjectPool<PooledResource> pool = new StackObjectPool<>(factory);
        PooledResource outstanding = pool.borrowObject();
        pool.addObject();

        pool.close();

        assertThat(pool.getNumIdle()).isZero();
        assertThatThrownBy(pool::borrowObject).isInstanceOf(IllegalStateException.class);
        pool.returnObject(outstanding);
        assertThat(outstanding.isDestroyed()).isTrue();
        assertThat(factory.destroyed()).hasSize(2);
    }

    @Test
    void poolFactoryCreatesBoundedStackPools() throws Exception {
        PooledResourceFactory objectFactory = new PooledResourceFactory("factory");
        StackObjectPoolFactory<PooledResource> poolFactory = new StackObjectPoolFactory<>(objectFactory, 1, 4);

        assertThat(poolFactory.getFactory()).isSameAs(objectFactory);
        assertThat(poolFactory.getMaxSleeping()).isEqualTo(1);
        assertThat(poolFactory.getInitCapacity()).isEqualTo(4);

        ObjectPool<PooledResource> pool = poolFactory.createPool();
        assertThat(pool).isInstanceOf(StackObjectPool.class);
        PooledResource first = pool.borrowObject();
        PooledResource second = pool.borrowObject();
        pool.returnObject(first);
        pool.returnObject(second);
        assertThat(pool.getNumIdle()).isEqualTo(1);
        assertThat(objectFactory.destroyed()).containsExactly(first);
        pool.close();
    }
}
