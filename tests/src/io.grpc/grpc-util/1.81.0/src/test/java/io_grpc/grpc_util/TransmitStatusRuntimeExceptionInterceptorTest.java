/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.util.TransmitStatusRuntimeExceptionInterceptor;

public class TransmitStatusRuntimeExceptionInterceptorTest {
    private static final Metadata.Key<String> ERROR_DETAIL = Metadata.Key.of(
            "error-detail", Metadata.ASCII_STRING_MARSHALLER);

    @Test
    void transmitsThrownStatusDescriptionAndTrailersToTheClient() throws Exception {
        Metadata trailers = new Metadata();
        trailers.put(ERROR_DETAIL, "account-read-only");
        ServerServiceDefinition service = RpcTestSupport.service((request, observer) -> {
            throw Status.PERMISSION_DENIED.withDescription("Updates are not allowed")
                    .asRuntimeException(trailers);
        });
        String name = InProcessServerBuilder.generateName();

        try (RpcTestSupport rpc = new RpcTestSupport()) {
            rpc.startServer(InProcessServerBuilder.forName(name).addService(service)
                    .intercept(TransmitStatusRuntimeExceptionInterceptor.instance()));
            ManagedChannel channel = rpc.openChannel(InProcessChannelBuilder.forName(name));

            StatusRuntimeException exception = assertThrows(StatusRuntimeException.class,
                    () -> RpcTestSupport.call(channel, "update"));
            assertThat(exception.getStatus().getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
            assertThat(exception.getStatus().getDescription()).isEqualTo("Updates are not allowed");
            assertThat(exception.getTrailers()).isNotNull();
            assertThat(exception.getTrailers().get(ERROR_DETAIL)).isEqualTo("account-read-only");
        }
    }

    @Test
    void transmitsStatusWhenTheExceptionHasNoTrailers() throws Exception {
        ServerServiceDefinition service = RpcTestSupport.service((request, observer) -> {
            throw Status.INVALID_ARGUMENT.withDescription("Request must contain a name").asRuntimeException();
        });
        String name = InProcessServerBuilder.generateName();

        try (RpcTestSupport rpc = new RpcTestSupport()) {
            rpc.startServer(InProcessServerBuilder.forName(name).addService(service)
                    .intercept(TransmitStatusRuntimeExceptionInterceptor.instance()));
            ManagedChannel channel = rpc.openChannel(InProcessChannelBuilder.forName(name));

            StatusRuntimeException exception = assertThrows(StatusRuntimeException.class,
                    () -> RpcTestSupport.call(channel, ""));
            assertThat(exception.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
            assertThat(exception.getStatus().getDescription()).isEqualTo("Request must contain a name");
            assertThat(exception.getTrailers()).isNotNull();
            assertThat(exception.getTrailers().get(ERROR_DETAIL)).isNull();
        }
    }

    @Test
    void preservesSuccessfulUnaryResponses() throws Exception {
        String name = InProcessServerBuilder.generateName();
        try (RpcTestSupport rpc = new RpcTestSupport()) {
            rpc.startServer(InProcessServerBuilder.forName(name)
                    .addService(RpcTestSupport.echoService("accepted:"))
                    .intercept(TransmitStatusRuntimeExceptionInterceptor.instance()));
            ManagedChannel channel = rpc.openChannel(InProcessChannelBuilder.forName(name));

            assertThat(RpcTestSupport.call(channel, "message")).isEqualTo("accepted:message");
            assertThat(RpcTestSupport.call(channel, "next")).isEqualTo("accepted:next");
        }
    }
}
