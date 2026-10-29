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

import org.apache.commons.pool.BaseKeyedPoolableObjectFactory;

/// A keyed factory that names resources `<key>-<n>` and records every one it creates and destroys.
public class KeyedPooledResourceFactory extends BaseKeyedPoolableObjectFactory<String, PooledResource> {
    private final AtomicInteger sequence = new AtomicInteger();
    private final List<PooledResource> created = new CopyOnWriteArrayList<>();
    private final List<PooledResource> destroyed = new CopyOnWriteArrayList<>();

    public List<PooledResource> created() {
        return new ArrayList<>(created);
    }

    public List<PooledResource> destroyed() {
        return new ArrayList<>(destroyed);
    }

    @Override
    public PooledResource makeObject(String key) {
        PooledResource resource = new PooledResource(key + "-" + sequence.incrementAndGet());
        created.add(resource);
        return resource;
    }

    @Override
    public void activateObject(String key, PooledResource resource) {
        resource.activate();
    }

    @Override
    public void passivateObject(String key, PooledResource resource) {
        resource.passivate();
    }

    @Override
    public boolean validateObject(String key, PooledResource resource) {
        return resource.isHealthy() && resource.getName().startsWith(key + "-");
    }

    @Override
    public void destroyObject(String key, PooledResource resource) {
        resource.destroy();
        destroyed.add(resource);
    }
}
