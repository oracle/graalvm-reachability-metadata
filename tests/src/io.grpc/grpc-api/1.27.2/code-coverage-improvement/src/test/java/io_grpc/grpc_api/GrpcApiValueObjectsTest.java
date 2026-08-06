/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_api;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.Attributes;
import io.grpc.CallOptions;
import io.grpc.Codec;
import io.grpc.CompressorRegistry;
import io.grpc.ConnectivityState;
import io.grpc.ConnectivityStateInfo;
import io.grpc.Deadline;
import io.grpc.DecompressorRegistry;
import io.grpc.EquivalentAddressGroup;
import io.grpc.InternalLogId;
import io.grpc.InternalMetadata;
import io.grpc.LoadBalancer;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.NameResolver;
import io.grpc.ServiceDescriptor;
import io.grpc.Status;
import io.grpc.SynchronizationContext;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class GrpcApiValueObjectsTest {
    private static final MethodDescriptor.Marshaller<String> STRING_MARSHALLER =
            new MethodDescriptor.Marshaller<String>() {
                @Override
                public InputStream stream(String value) {
                    return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
                }

                @Override
                public String parse(InputStream stream) {
                    try {
                        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                    } catch (java.io.IOException exception) {
                        throw new AssertionError(exception);
                    }
                }
            };

    @Test
    void attributesAndCallOptionsPreserveConfiguredValues() {
        Attributes.Key<String> name = Attributes.Key.create("name");
        Attributes.Key<Integer> count = Attributes.Key.of("count");
        Attributes original = Attributes.newBuilder().set(name, "grpc").set(count, 2).build();
        Attributes derived = original.toBuilder().discard(count).setAll(original).build();

        assertThat(derived).isEqualTo(original);
        assertThat(derived.get(name)).isEqualTo("grpc");
        assertThat(derived.keys()).containsExactlyInAnyOrder(name, count);
        assertThat(derived.toString()).contains("name");
        assertThat(name.toString()).isEqualTo("name");
        assertThat(original.hashCode()).isEqualTo(derived.hashCode());

        CallOptions.Key<String> option = CallOptions.Key.createWithDefault("tenant", "default");
        CallOptions options = CallOptions.DEFAULT
                .withAuthority("api.example")
                .withCompression("gzip")
                .withDeadline(Deadline.after(2, TimeUnit.SECONDS))
                .withDeadlineAfter(1, TimeUnit.SECONDS)
                .withMaxInboundMessageSize(1024)
                .withMaxOutboundMessageSize(2048)
                .withOption(option, "blue")
                .withWaitForReady()
                .withoutWaitForReady();

        assertThat(options.getAuthority()).isEqualTo("api.example");
        assertThat(options.getCompressor()).isEqualTo("gzip");
        assertThat(options.getDeadline()).isNotNull();
        assertThat(options.getMaxInboundMessageSize()).isEqualTo(1024);
        assertThat(options.getMaxOutboundMessageSize()).isEqualTo(2048);
        assertThat(options.getOption(option)).isEqualTo("blue");
        assertThat(options.isWaitForReady()).isFalse();
        assertThat(options.getStreamTracerFactories()).isEmpty();
        assertThat(options.toString()).contains("api.example");
        assertThat(option.getDefault()).isEqualTo("default");
        assertThat(CallOptions.Key.of("fallback", "value").toString()).isEqualTo("fallback");
    }

    @Test
    void codecsRegistriesAndMetadataRoundTripUserData() throws Exception {
        byte[] message = "compressed grpc payload".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (java.io.OutputStream stream = new Codec.Gzip().compress(compressed)) {
            stream.write(message);
        }
        byte[] restored;
        try (InputStream stream = new Codec.Gzip().decompress(new ByteArrayInputStream(compressed.toByteArray()))) {
            restored = stream.readAllBytes();
        }
        assertThat(restored).isEqualTo(message);
        assertThat(Codec.Identity.NONE.compress(compressed)).isSameAs(compressed);
        assertThat(Codec.Identity.NONE.getMessageEncoding()).isEqualTo("identity");
        assertThat(new Codec.Gzip().getMessageEncoding()).isEqualTo("gzip");
        CompressorRegistry compressors = CompressorRegistry.newEmptyInstance();
        compressors.register(new Codec.Gzip());
        assertThat(compressors.lookupCompressor("gzip")).isNotNull();
        assertThat(CompressorRegistry.getDefaultInstance().lookupCompressor("gzip")).isNotNull();
        assertThat(DecompressorRegistry.emptyInstance().with(new Codec.Gzip(), true)
                .lookupDecompressor("gzip")).isNotNull();

        Metadata.Key<String> header = Metadata.Key.of("x-tenant", Metadata.ASCII_STRING_MARSHALLER);
        Metadata.Key<byte[]> binary = Metadata.Key.of("trace-bin", Metadata.BINARY_BYTE_MARSHALLER);
        Metadata metadata = new Metadata();
        metadata.put(header, "blue");
        metadata.put(header, "green");
        metadata.put(binary, new byte[] {1, 2});
        assertThat(metadata.containsKey(header)).isTrue();
        assertThat(metadata.get(header)).isEqualTo("green");
        assertThat(metadata.getAll(header)).containsExactly("blue", "green");
        assertThat(metadata.remove(header, "blue")).isTrue();
        assertThat(metadata.removeAll(header)).containsExactly("green");
        metadata.merge(new Metadata());
        metadata.discardAll(binary);
        assertThat(metadata.keys()).doesNotContain("trace-bin");
        assertThat(header.name()).isEqualTo("x-tenant");
        assertThat(header.originalName()).isEqualTo("x-tenant");
        assertThat(header).isEqualTo(Metadata.Key.of("x-tenant", Metadata.ASCII_STRING_MARSHALLER));
    }

    @Test
    void descriptorsAndResolverValuesRetainTheirSemanticConfiguration() {
        MethodDescriptor<String, String> method = MethodDescriptor.newBuilder(STRING_MARSHALLER, STRING_MARSHALLER)
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(MethodDescriptor.generateFullMethodName("echo.Echo", "Say"))
                .setSchemaDescriptor("schema")
                .setIdempotent(true)
                .setSafe(true)
                .setSampledToLocalTracing(true)
                .build();
        assertThat(method.getServiceName()).isEqualTo("echo.Echo");
        assertThat(MethodDescriptor.extractFullServiceName(method.getFullMethodName())).isEqualTo("echo.Echo");
        assertThat(method.parseRequest(method.streamRequest("request"))).isEqualTo("request");
        assertThat(method.parseResponse(method.streamResponse("response"))).isEqualTo("response");
        assertThat(method.isIdempotent()).isTrue();
        assertThat(method.isSafe()).isTrue();
        assertThat(method.isSampledToLocalTracing()).isTrue();
        MethodDescriptor<String, String> rebuilt = method.toBuilder().build();
        assertThat(rebuilt.getFullMethodName()).isEqualTo(method.getFullMethodName());
        assertThat(rebuilt.getSchemaDescriptor()).isEqualTo("schema");

        ServiceDescriptor service = ServiceDescriptor.newBuilder("echo.Echo").setSchemaDescriptor("schema")
                .addMethod(method).build();
        assertThat(service.getName()).isEqualTo("echo.Echo");
        assertThat(service.getMethods()).containsExactly(method);
        assertThat(service.toString()).contains("echo.Echo");

        EquivalentAddressGroup address = new EquivalentAddressGroup(new InetSocketAddress("localhost", 443));
        EquivalentAddressGroup attributedAddress = new EquivalentAddressGroup(
                Collections.singletonList(new InetSocketAddress("localhost", 443)), Attributes.EMPTY);
        assertThat(attributedAddress.getAddresses()).containsExactly(new InetSocketAddress("localhost", 443));
        assertThat(attributedAddress.getAttributes()).isEqualTo(Attributes.EMPTY);
        assertThat(attributedAddress.hashCode()).isEqualTo(new EquivalentAddressGroup(
                Collections.singletonList(new InetSocketAddress("localhost", 443)), Attributes.EMPTY).hashCode());
        LoadBalancer.ResolvedAddresses addresses = LoadBalancer.ResolvedAddresses.newBuilder()
                .setAddresses(Collections.singletonList(address)).setAttributes(Attributes.EMPTY)
                .setLoadBalancingPolicyConfig("round_robin").build();
        assertThat(addresses.toBuilder().build()).isEqualTo(addresses);
        assertThat(addresses.getAddresses()).containsExactly(address);
        assertThat(addresses.getLoadBalancingPolicyConfig()).isEqualTo("round_robin");

        NameResolver.ConfigOrError config = NameResolver.ConfigOrError.fromConfig("service-config");
        NameResolver.ResolutionResult result = NameResolver.ResolutionResult.newBuilder()
                .setAddresses(Collections.singletonList(address)).setAttributes(Attributes.EMPTY)
                .setServiceConfig(config).build();
        assertThat(result.toBuilder().build()).isEqualTo(result);
        assertThat(result.getServiceConfig().getConfig()).isEqualTo("service-config");
        assertThat(NameResolver.ConfigOrError.fromError(Status.UNAVAILABLE).getError()).isEqualTo(Status.UNAVAILABLE);
    }

    @Test
    void internalMetadataAndLogIdentifiersExposeWireAndDiagnosticValues() {
        Metadata.Key<String> key = InternalMetadata.keyOf("x-route", Metadata.ASCII_STRING_MARSHALLER);
        Metadata original = new Metadata();
        original.put(key, "blue");
        byte[][] serialized = InternalMetadata.serialize(original);
        Metadata restored = InternalMetadata.newMetadata(serialized);
        assertThat(InternalMetadata.headerCount(restored)).isEqualTo(1);
        assertThat(restored.get(key)).isEqualTo("blue");
        assertThat(InternalMetadata.serializePartial(restored)).hasSize(2);
        Object[] parsedValues = InternalMetadata.serializePartial(restored);
        assertThat(InternalMetadata.newMetadataWithParsedValues(1, parsedValues)).isNotNull();

        InternalLogId id = InternalLogId.allocate(GrpcApiValueObjectsTest.class, "test");
        assertThat(id.getTypeName()).isEqualTo("GrpcApiValueObjectsTest");
        assertThat(id.getDetails()).isEqualTo("test");
        assertThat(id.getId()).isPositive();
        assertThat(id.shortName()).contains("GrpcApiValueObjectsTest");
        assertThat(id.toString()).contains("test");
    }

    @Test
    void synchronizationContextSerializesImmediateAndScheduledWork() {
        AtomicInteger completed = new AtomicInteger();
        SynchronizationContext context = new SynchronizationContext((thread, error) -> {
            throw new AssertionError(error);
        });
        context.executeLater(completed::incrementAndGet);
        assertThat(completed).hasValue(0);
        context.drain();
        assertThat(completed).hasValue(1);
        context.execute(completed::incrementAndGet);
        assertThat(completed).hasValue(2);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            SynchronizationContext.ScheduledHandle handle = context.schedule(
                    completed::incrementAndGet, 1, TimeUnit.DAYS, scheduler);
            assertThat(handle.isPending()).isTrue();
            handle.cancel();
            assertThat(handle.isPending()).isFalse();
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void statusAndConnectivityExposeFailureDetails() {
        IllegalStateException cause = new IllegalStateException("offline");
        Status status = Status.UNAVAILABLE.withDescription("backend unavailable").withCause(cause)
                .augmentDescription("retry later");
        assertThat(status.getCode()).isEqualTo(Status.Code.UNAVAILABLE);
        assertThat(status.getDescription()).contains("backend unavailable", "retry later");
        assertThat(status.getCause()).isSameAs(cause);
        assertThat(Status.fromCodeValue(14)).isEqualTo(Status.UNAVAILABLE);
        assertThat(Status.fromThrowable(status.asRuntimeException())).isEqualTo(status);
        assertThat(Status.trailersFromThrowable(status.asException())).isNull();
        assertThat(status.asException().getStatus()).isEqualTo(status);
        assertThat(status.asRuntimeException().getStatus()).isEqualTo(status);

        ConnectivityStateInfo ready = ConnectivityStateInfo.forNonError(ConnectivityState.READY);
        ConnectivityStateInfo failure = ConnectivityStateInfo.forTransientFailure(Status.UNAVAILABLE);
        assertThat(ready.getState()).isEqualTo(ConnectivityState.READY);
        assertThat(ready.getStatus()).isEqualTo(Status.OK);
        assertThat(failure.getStatus()).isEqualTo(Status.UNAVAILABLE);
        assertThat(ready).isEqualTo(ConnectivityStateInfo.forNonError(ConnectivityState.READY));
        assertThat(failure.toString()).contains("UNAVAILABLE");
    }
}
