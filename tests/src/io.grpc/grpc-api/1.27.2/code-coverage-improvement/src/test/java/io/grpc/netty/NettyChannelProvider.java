/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io.grpc.netty;

import io.grpc.ManagedChannelBuilder;
import io.grpc.ManagedChannelProvider;

/** Test-only optional Android transport discovered through grpc-api's public provider API. */
public class NettyChannelProvider extends ManagedChannelProvider {
    @Override
    protected boolean isAvailable() {
        return true;
    }

    @Override
    protected int priority() {
        return 7;
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
