/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import io.grpc.netty.shaded.io.netty.util.ResourceLeakDetector;

public final class ConfiguredLeakDetector<T> extends ResourceLeakDetector<T> {
    final Class<?> resourceType;
    final int interval;
    final long maxActive;
    final boolean legacy;

    public ConfiguredLeakDetector(Class<?> resourceType, int interval) {
        super(resourceType, interval);
        this.resourceType = resourceType;
        this.interval = interval;
        this.maxActive = Long.MAX_VALUE;
        this.legacy = false;
    }

    @SuppressWarnings("deprecation")
    public ConfiguredLeakDetector(Class<?> resourceType, int interval, long maxActive) {
        super(resourceType, interval, maxActive);
        this.resourceType = resourceType;
        this.interval = interval;
        this.maxActive = maxActive;
        this.legacy = true;
    }
}
