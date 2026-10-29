/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_pool.commons_pool;

/// A pooled object that records the lifecycle callbacks a pool drives on it.
public final class PooledResource {
    private final String name;
    private volatile boolean active;
    private volatile boolean healthy = true;
    private volatile boolean destroyed;
    private volatile int activations;

    public PooledResource(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isHealthy() {
        return healthy;
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    public int getActivations() {
        return activations;
    }

    /// Makes the factory report this resource as invalid from now on.
    public void markUnhealthy() {
        healthy = false;
    }

    void activate() {
        active = true;
        activations++;
    }

    void passivate() {
        active = false;
    }

    void destroy() {
        active = false;
        destroyed = true;
    }

    @Override
    public String toString() {
        return name;
    }
}
