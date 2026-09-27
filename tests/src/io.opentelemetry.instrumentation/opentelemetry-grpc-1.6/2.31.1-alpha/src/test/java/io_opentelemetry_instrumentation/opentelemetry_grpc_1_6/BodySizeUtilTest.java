/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry_instrumentation.opentelemetry_grpc_1_6;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.StringValue;
import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcRequest;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class BodySizeUtilTest {
    private static final String SERVICE_NAME = "test.SizeService";
    private static final MethodDescriptor<StringValue, StringValue> ECHO_METHOD = MethodDescriptor
            .<StringValue, StringValue>newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, "Echo"))
            .setRequestMarshaller(new StringValueMarshaller())
            .setResponseMarshaller(new StringValueMarshaller())
            .build();

    @Test
    void instrumentedUnaryCallCapturesProtobufRequestAndResponseSizes() throws Exception {
        AtomicReference<MessageSizes> clientSizes = new AtomicReference<>();
        AtomicReference<MessageSizes> serverSizes = new AtomicReference<>();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder().build();
        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
        GrpcTelemetry telemetry = GrpcTelemetry.builder(openTelemetry)
                .addClientAttributeExtractor(new SizeExtractor(clientSizes))
                .addServerAttributeExtractor(new SizeExtractor(serverSizes))
                .build();
        String serverName = InProcessServerBuilder.generateName();
        Server server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(ServerInterceptors.intercept(echoService(), telemetry.createServerInterceptor()))
                .build()
                .start();
        ManagedChannel channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .intercept(telemetry.createClientInterceptor())
                .build();

        StringValue request = StringValue.of("measure this protobuf request");
        StringValue expectedResponse = StringValue.of("echo: " + request.getValue());
        try {
            StringValue response = ClientCalls.blockingUnaryCall(
                    channel,
                    ECHO_METHOD,
                    CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS),
                    request);

            assertThat(response).isEqualTo(expectedResponse);
            assertThat(clientSizes.get())
                    .isEqualTo(new MessageSizes(request.getSerializedSize(), expectedResponse.getSerializedSize()));
            assertThat(serverSizes.get())
                    .isEqualTo(new MessageSizes(request.getSerializedSize(), expectedResponse.getSerializedSize()));
        } finally {
            channel.shutdownNow();
            assertThat(channel.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            server.shutdownNow();
            assertThat(server.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            assertThat(tracerProvider.shutdown().join(10, TimeUnit.SECONDS).isSuccess()).isTrue();
            openTelemetry.close();
        }
    }

    private static ServerServiceDefinition echoService() {
        return ServerServiceDefinition.builder(SERVICE_NAME)
                .addMethod(ECHO_METHOD, ServerCalls.asyncUnaryCall((request, observer) -> {
                    observer.onNext(StringValue.of("echo: " + request.getValue()));
                    observer.onCompleted();
                }))
                .build();
    }

    private static final class SizeExtractor implements AttributesExtractor<GrpcRequest, Status> {
        private final AtomicReference<MessageSizes> sizes;

        private SizeExtractor(AtomicReference<MessageSizes> sizes) {
            this.sizes = sizes;
        }

        @Override
        public void onStart(AttributesBuilder attributes, Context context, GrpcRequest request) {
        }

        @Override
        public void onEnd(
                AttributesBuilder attributes,
                Context context,
                GrpcRequest request,
                Status response,
                Throwable error) {
            sizes.set(new MessageSizes(request.getRequestSize(), request.getResponseSize()));
        }
    }

    private static final class StringValueMarshaller implements MethodDescriptor.Marshaller<StringValue> {
        @Override
        public InputStream stream(StringValue value) {
            return new ByteArrayInputStream(value.toByteArray());
        }

        @Override
        public StringValue parse(InputStream stream) {
            try {
                return StringValue.parseFrom(stream);
            } catch (IOException exception) {
                throw Status.INTERNAL.withCause(exception).asRuntimeException();
            }
        }
    }

    private record MessageSizes(long request, long response) {
    }
}
