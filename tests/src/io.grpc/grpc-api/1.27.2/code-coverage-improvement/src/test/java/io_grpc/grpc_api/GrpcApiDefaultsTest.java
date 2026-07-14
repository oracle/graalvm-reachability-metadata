/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_api;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.Attributes;
import io.grpc.CallCredentials;
import io.grpc.CallCredentials2;
import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.ClientStreamTracer;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.LoadBalancer;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StreamTracer;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class GrpcApiDefaultsTest {
    @Test
    void credentialsAndCallOptionsCarryAuthenticationAndExecutionPolicy() {
        AtomicReference<String> applied = new AtomicReference<>();
        CallCredentials credential = new CallCredentials2() {
            @Override
            public void applyRequestMetadata(RequestInfo requestInfo, Executor executor, MetadataApplier applier) {
                executor.execute(() -> {
                    Metadata headers = new Metadata();
                    headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer token");
                    applier.apply(headers);
                });
            }

            @Override
            public void thisUsesUnstableApi() {}
        };
        CallOptions options = CallOptions.DEFAULT.withCallCredentials(credential).withExecutor(Runnable::run)
                .withStreamTracerFactory(new ClientStreamTracer.Factory() {});
        assertThat(options.getCredentials()).isSameAs(credential);
        assertThat(options.getExecutor()).isNotNull();
        assertThat(options.getStreamTracerFactories()).hasSize(1);

        credential.applyRequestMetadata(null, options.getExecutor(), new CallCredentials.MetadataApplier() {
            @Override
            public void apply(Metadata headers) {
                applied.set(headers.get(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)));
            }

            @Override
            public void fail(Status status) {
                throw new AssertionError(status);
            }
        });
        assertThat(applied).hasValue("Bearer token");
        assertThat(CallOptions.Key.create("request-id").toString()).isEqualTo("request-id");
    }

    @Test
    void tracerDefaultsAndLoadBalancerPickResultsProvideNeutralBehavior() {
        ClientStreamTracer tracer = new ClientStreamTracer() {};
        tracer.outboundHeaders();
        tracer.inboundHeaders();
        tracer.inboundTrailers(new Metadata());
        StreamTracer streamTracer = tracer;
        streamTracer.outboundMessage(1);
        streamTracer.outboundMessageSent(1, 2, 3);
        streamTracer.outboundWireSize(4);
        streamTracer.outboundUncompressedSize(5);
        streamTracer.inboundMessage(6);
        streamTracer.inboundMessageRead(6, 7, 8);
        streamTracer.inboundWireSize(9);
        streamTracer.inboundUncompressedSize(10);
        streamTracer.streamClosed(Status.OK);

        LoadBalancer.PickResult noResult = LoadBalancer.PickResult.withNoResult();
        LoadBalancer.PickResult error = LoadBalancer.PickResult.withError(Status.UNAVAILABLE);
        LoadBalancer.PickResult drop = LoadBalancer.PickResult.withDrop(Status.RESOURCE_EXHAUSTED);
        assertThat(noResult.getSubchannel()).isNull();
        assertThat(error.getStatus()).isEqualTo(Status.UNAVAILABLE);
        assertThat(drop.isDrop()).isTrue();
        assertThat(error).isEqualTo(LoadBalancer.PickResult.withError(Status.UNAVAILABLE));
        assertThat(error.hashCode()).isEqualTo(LoadBalancer.PickResult.withError(Status.UNAVAILABLE).hashCode());
        assertThat(drop.toString()).contains("RESOURCE_EXHAUSTED");
    }

    @Test
    void cancelledContextsExposeCancellationStatusAndPreserveCallAttributes() {
        Context.CancellableContext cancelled = Context.current().withCancellation();
        cancelled.cancel(new IllegalStateException("client stopped"));
        Status cancellationStatus = Contexts.statusFromCancelled(cancelled);
        assertThat(cancellationStatus.getCode()).isEqualTo(Status.Code.CANCELLED);
        assertThat(cancellationStatus.getDescription()).isEqualTo("Context cancelled");
        assertThat(cancellationStatus.getCause()).isSameAs(cancelled.cancellationCause());

        ClientCall<String, String> call = new ClientCall<String, String>() {
            @Override public void start(Listener<String> listener, Metadata headers) {}
            @Override public void request(int count) {}
            @Override public void cancel(String message, Throwable cause) {}
            @Override public void halfClose() {}
            @Override public void sendMessage(String message) {}
        };
        assertThat(call.getAttributes()).isEqualTo(Attributes.EMPTY);
        assertThat(call.isReady()).isTrue();
        call.setMessageCompression(true);
        new ClientCall.Listener<String>() {}.onMessage("response");
    }
}
