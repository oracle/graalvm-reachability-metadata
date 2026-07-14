/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io.grpc.okhttp;

import io.grpc.ManagedChannelBuilder;
import io.grpc.ManagedChannelProvider;

/** Test-only Android fallback transport discovered through grpc-api's public provider API. */
public class OkHttpChannelProvider extends ManagedChannelProvider {
    @Override
    protected boolean isAvailable() {
        return true;
    }

    @Override
    protected int priority() {
        return 3;
    }

    @Override
    protected ManagedChannelBuilder<?> builderForAddress(String name, int port) {
        return null;
    }

    @Override
    protected ManagedChannelBuilder<?> builderForTarget(String target) {
        return null;
    }
}
