/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_pool.commons_pool;

import java.util.NoSuchElementException;

import org.apache.commons.pool.impl.SoftReferenceObjectPool;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// The factory keeps every resource strongly reachable, so no soft reference is cleared mid-test.
public class SoftReferenceObjectPoolTest {

    @Test
    void reusesReturnedObjects() throws Exception {
        PooledResourceFactory factory = new PooledResourceFactory("soft");
        SoftReferenceObjectPool<PooledResource> pool = new SoftReferenceObjectPool<>(factory);

        PooledResource resource = pool.borrowObject();
        assertThat(resource.isActive()).isTrue();
        assertThat(pool.getNumActive()).isEqualTo(1);
        pool.returnObject(resource);
        assertThat(resource.isActive()).isFalse();
        assertThat(pool.getNumIdle()).isEqualTo(1);

        assertThat(pool.borrowObject()).isSameAs(resource);
        assertThat(factory.created()).containsExactly(resource);
        pool.close();
    }

    @Test
    void prefillsTheRequestedNumberOfIdleObjects() throws Exception {
        PooledResourceFactory factory = new PooledResourceFactory("prefilled");
        SoftReferenceObjectPool<PooledResource> pool = new SoftReferenceObjectPool<>(factory, 3);

        assertThat(pool.getNumIdle()).isEqualTo(3);
        assertThat(pool.getFactory()).isSameAs(factory);
        assertThat(factory.created()).contains(pool.borrowObject()).hasSize(3);
        pool.close();
    }

    @Test
    void destroysReturnedObjectsThatFailValidation() throws Exception {
        PooledResourceFactory factory = new PooledResourceFactory("invalid");
        SoftReferenceObjectPool<PooledResource> pool = new SoftReferenceObjectPool<>(factory);
        PooledResource resource = pool.borrowObject();
        resource.markUnhealthy();

        pool.returnObject(resource);

        assertThat(resource.isDestroyed()).isTrue();
        assertThat(pool.getNumIdle()).isZero();
        assertThat(pool.getNumActive()).isZero();
        pool.close();
    }

    @Test
    void reportsFactoriesThatCannotCreateValidObjects() throws Exception {
        PooledResourceFactory factory = new PooledResourceFactory("never-valid");
        factory.createUnhealthyResources();
        SoftReferenceObjectPool<PooledResource> pool = new SoftReferenceObjectPool<>(factory);

        assertThatThrownBy(pool::borrowObject)
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Could not create a validated object");
        assertThat(factory.destroyed()).containsExactlyElementsOf(factory.created());
        pool.close();
    }

    @Test
    void invalidateClearAndCloseDestroyObjects() throws Exception {
        PooledResourceFactory factory = new PooledResourceFactory("lifecycle");
        SoftReferenceObjectPool<PooledResource> pool = new SoftReferenceObjectPool<>(factory);
        PooledResource invalidated = pool.borrowObject();
        pool.invalidateObject(invalidated);
        assertThat(invalidated.isDestroyed()).isTrue();
        assertThat(pool.getNumActive()).isZero();

        pool.addObject();
        pool.addObject();
        pool.clear();
        assertThat(pool.getNumIdle()).isZero();
        assertThat(factory.destroyed()).hasSize(3);

        pool.addObject();
        pool.close();
        assertThat(factory.destroyed()).containsExactlyInAnyOrderElementsOf(factory.created());
        assertThatThrownBy(pool::borrowObject).isInstanceOf(IllegalStateException.class);
    }
}
