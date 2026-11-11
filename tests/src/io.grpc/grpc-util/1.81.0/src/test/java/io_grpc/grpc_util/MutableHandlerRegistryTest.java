/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.grpc.BindableService;
import io.grpc.ManagedChannel;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.util.MutableHandlerRegistry;

public class MutableHandlerRegistryTest {
    @Test
    void updatesServicesOnARunningServer() throws Exception {
        MutableHandlerRegistry registry = new MutableHandlerRegistry();
        ServerServiceDefinition first = RpcTestSupport.echoService("first:");
        ServerServiceDefinition replacement = RpcTestSupport.echoService("replacement:");
        String name = InProcessServerBuilder.generateName();

        try (RpcTestSupport rpc = new RpcTestSupport()) {
            rpc.startServer(InProcessServerBuilder.forName(name).fallbackHandlerRegistry(registry));
            ManagedChannel channel = rpc.openChannel(InProcessChannelBuilder.forName(name));

            assertThat(registry.addService(first)).isNull();
            assertThat(RpcTestSupport.call(channel, "hello")).isEqualTo("first:hello");
            assertThat(registry.addService(replacement)).isSameAs(first);
            assertThat(registry.removeService(first)).isFalse();
            assertThat(RpcTestSupport.call(channel, "hello")).isEqualTo("replacement:hello");
            assertThat(registry.getServices()).containsExactly(replacement);

            assertThat(registry.removeService(replacement)).isTrue();
            StatusRuntimeException missing = assertThrows(StatusRuntimeException.class,
                    () -> RpcTestSupport.call(channel, "hello"));
            assertThat(missing.getStatus().getCode()).isEqualTo(Status.Code.UNIMPLEMENTED);
            assertThat(registry.getServices()).isEmpty();

            assertThat(registry.addService(first)).isNull();
            assertThat(RpcTestSupport.call(channel, "again")).isEqualTo("first:again");
        }
    }

    @Test
    void bindsServicesAndLooksUpMethodsWithoutExposingMutableServiceLists() {
        MutableHandlerRegistry registry = new MutableHandlerRegistry();
        ServerServiceDefinition definition = RpcTestSupport.echoService("reply:");
        BindableService service = () -> definition;

        assertThat(registry.addService(service)).isNull();
        assertThat(registry.lookupMethod(RpcTestSupport.METHOD.getFullMethodName(), null))
                .isSameAs(definition.getMethod(RpcTestSupport.METHOD.getFullMethodName()));
        assertThat(registry.lookupMethod("reachability.UtilityService/Unknown", null)).isNull();
        assertThat(registry.lookupMethod("reachability.Unknown/Reply", null)).isNull();
        assertThat(registry.lookupMethod("no-service-separator", null)).isNull();

        List<ServerServiceDefinition> snapshot = registry.getServices();
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        assertThat(registry.removeService(definition)).isTrue();
        assertThat(registry.removeService(definition)).isFalse();
        assertThat(snapshot).containsExactly(definition);
        assertThat(registry.lookupMethod(RpcTestSupport.METHOD.getFullMethodName(), null)).isNull();
    }
}
