/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_pool.commons_pool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.pool.BasePoolableObjectFactory;

/// A factory that names resources `<prefix>-<n>` and records every one it creates and destroys.
public class PooledResourceFactory extends BasePoolableObjectFactory<PooledResource> {
    private final String prefix;
    private final AtomicInteger sequence = new AtomicInteger();
    private final List<PooledResource> created = new CopyOnWriteArrayList<>();
    private final List<PooledResource> destroyed = new CopyOnWriteArrayList<>();
    private volatile boolean createUnhealthy;

    public PooledResourceFactory(String prefix) {
        this.prefix = prefix;
    }

    /// Makes every resource created from now on fail validation.
    public void createUnhealthyResources() {
        createUnhealthy = true;
    }

    public List<PooledResource> created() {
        return new ArrayList<>(created);
    }

    public List<PooledResource> destroyed() {
        return new ArrayList<>(destroyed);
    }

    @Override
    public PooledResource makeObject() {
        PooledResource resource = new PooledResource(prefix + "-" + sequence.incrementAndGet());
        if (createUnhealthy) {
            resource.markUnhealthy();
        }
        created.add(resource);
        return resource;
    }

    @Override
    public void activateObject(PooledResource resource) {
        resource.activate();
    }

    @Override
    public void passivateObject(PooledResource resource) {
        resource.passivate();
    }

    @Override
    public boolean validateObject(PooledResource resource) {
        return resource.isHealthy();
    }

    @Override
    public void destroyObject(PooledResource resource) {
        resource.destroy();
        destroyed.add(resource);
    }
}
