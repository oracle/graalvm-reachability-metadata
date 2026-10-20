/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty;

import com.google.protobuf.Int64Value;
import com.google.protobuf.StringValue;
import io.grpc.CallOptions;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.NegotiationType;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Serves a shipping-rate service over the grpc-netty in-VM local transport, where
 * `NettyServerBuilder` listens on Netty `LocalAddress`es through `LocalServerChannel` and
 * `NettyChannelBuilder` dials them through `LocalChannel`, all on one application-owned
 * `DefaultEventLoopGroup`.
 *
 * A warehouse embeds the rate service in the same process as its dispatch module and wires
 * them with Netty's local transport instead of a loopback port. The server listens on a
 * primary and a backup local address, quotes parcels for callers on either one, totals a
 * manifest streamed by the client, sees the caller as a local address and the overridden
 * authority, refuses a dial to an address nobody listens on, and leaves the shared event
 * loop group running for the application to shut down.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ShippingRateLocalChannelTest {

    private static final String SERVICE = "logistics.ShippingRates";

    private static final String AUTHORITY = "rates.warehouse.internal";

    private static final LocalAddress PRIMARY = new LocalAddress("shipping-rates-primary");

    private static final LocalAddress BACKUP = new LocalAddress("shipping-rates-backup");

    private static final LocalAddress UNBOUND = new LocalAddress("shipping-rates-retired");

    private static final MethodDescriptor<StringValue, Int64Value> QUOTE =
            MethodDescriptor.<StringValue, Int64Value>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE, "Quote"))
                    .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .setResponseMarshaller(ProtoUtils.marshaller(Int64Value.getDefaultInstance()))
                    .build();

    private static final MethodDescriptor<StringValue, StringValue> MANIFEST =
            MethodDescriptor.<StringValue, StringValue>newBuilder()
                    .setType(MethodDescriptor.MethodType.CLIENT_STREAMING)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE, "Manifest"))
                    .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .setResponseMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .build();

    private final RateTable rates = new RateTable(Map.of("EU", 450L, "US", 900L, "APAC", 1_200L));

    private final List<String> callers = new CopyOnWriteArrayList<>();

    private EventLoopGroup eventLoops;

    private Server server;

    private ManagedChannel primaryChannel;

    private ManagedChannel backupChannel;

    @BeforeAll
    void startServerAndChannels() throws Exception {
        eventLoops = new DefaultEventLoopGroup(2);
        server = NettyServerBuilder.forAddress(PRIMARY)
                .addListenAddress(BACKUP)
                .channelType(LocalServerChannel.class)
                .bossEventLoopGroup(eventLoops)
                .workerEventLoopGroup(eventLoops)
                .addService(ServerInterceptors.intercept(rates.bindService(), new CallerInterceptor()))
                .build()
                .start();
        primaryChannel = localChannel(PRIMARY);
        backupChannel = localChannel(BACKUP);
    }

    @AfterAll
    void stopServerAndChannels() throws Exception {
        primaryChannel.shutdown();
        backupChannel.shutdown();
        server.shutdown();
        assertThat(primaryChannel.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(backupChannel.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(server.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(eventLoops.isShuttingDown()).isFalse();
        assertThat(eventLoops.shutdownGracefully(0, 5, TimeUnit.SECONDS).await(10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void listensOnBothLocalAddresses() {
        List<SocketAddress> listening = new ArrayList<>(server.getListenSockets());

        assertThat(listening).containsExactlyInAnyOrder(PRIMARY, BACKUP);
        assertThat(server.getPort()).isEqualTo(-1);
    }

    @Test
    void quotesParcelsThroughEitherLocalAddress() {
        Int64Value viaPrimary = ClientCalls.blockingUnaryCall(primaryChannel, QUOTE,
                CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS), text("EU 3"));
        Int64Value viaBackup = ClientCalls.blockingUnaryCall(backupChannel, QUOTE,
                CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS), text("APAC 2"));

        assertThat(viaPrimary.getValue()).isEqualTo(1_350L);
        assertThat(viaBackup.getValue()).isEqualTo(2_400L);
        assertThat(callers).isNotEmpty().containsOnly(AUTHORITY + " from local");

        assertThatThrownBy(() -> ClientCalls.blockingUnaryCall(backupChannel, QUOTE,
                CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS), text("MARS 1")))
                .isInstanceOfSatisfying(StatusRuntimeException.class, e -> {
                    assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
                    assertThat(e.getStatus().getDescription()).isEqualTo("no rate for zone MARS");
                });
    }

    @Test
    void totalsAStreamedManifest() throws Exception {
        AtomicReference<String> summary = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch completed = new CountDownLatch(1);

        StreamObserver<StringValue> parcels = ClientCalls.asyncClientStreamingCall(
                primaryChannel.newCall(MANIFEST, CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS)),
                new StreamObserver<StringValue>() {
                    @Override
                    public void onNext(StringValue value) {
                        summary.set(value.getValue());
                    }

                    @Override
                    public void onError(Throwable t) {
                        failure.set(t);
                        completed.countDown();
                    }

                    @Override
                    public void onCompleted() {
                        completed.countDown();
                    }
                });
        parcels.onNext(text("US 1"));
        parcels.onNext(text("EU 2"));
        parcels.onNext(text("US 4"));
        parcels.onCompleted();

        assertThat(completed.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(failure.get()).isNull();
        assertThat(summary.get()).isEqualTo("3 parcels, 7 kg, 5400 cents");
    }

    @Test
    void refusesADialToAnUnboundLocalAddress() throws Exception {
        ManagedChannel retired = localChannel(UNBOUND);
        try {
            assertThatThrownBy(() -> ClientCalls.blockingUnaryCall(retired, QUOTE,
                    CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS), text("EU 1")))
                    .isInstanceOfSatisfying(StatusRuntimeException.class, e ->
                            assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE));
        } finally {
            retired.shutdownNow();
            assertThat(retired.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private ManagedChannel localChannel(LocalAddress address) {
        return NettyChannelBuilder.forAddress(address)
                .channelType(LocalChannel.class)
                .eventLoopGroup(eventLoops)
                .negotiationType(NegotiationType.PLAINTEXT)
                .overrideAuthority(AUTHORITY)
                .build();
    }

    private static StringValue text(String value) {
        return StringValue.newBuilder().setValue(value).build();
    }

    /** The rate service, pricing parcels per kilogram by destination zone. */
    private static final class RateTable {

        private final Map<String, Long> centsPerKilogram;

        RateTable(Map<String, Long> centsPerKilogram) {
            this.centsPerKilogram = centsPerKilogram;
        }

        ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder(SERVICE)
                    .addMethod(QUOTE, ServerCalls.asyncUnaryCall(this::quote))
                    .addMethod(MANIFEST, ServerCalls.asyncClientStreamingCall(this::manifest))
                    .build();
        }

        private void quote(StringValue parcel, StreamObserver<Int64Value> response) {
            String[] parts = parcel.getValue().split(" ");
            Long rate = centsPerKilogram.get(parts[0]);
            if (rate == null) {
                response.onError(Status.INVALID_ARGUMENT
                        .withDescription("no rate for zone " + parts[0])
                        .asRuntimeException());
                return;
            }
            response.onNext(Int64Value.of(rate * Long.parseLong(parts[1])));
            response.onCompleted();
        }

        private StreamObserver<StringValue> manifest(StreamObserver<StringValue> response) {
            return new StreamObserver<StringValue>() {
                private int parcels;

                private long kilograms;

                private long cents;

                @Override
                public void onNext(StringValue parcel) {
                    String[] parts = parcel.getValue().split(" ");
                    long weight = Long.parseLong(parts[1]);
                    parcels++;
                    kilograms += weight;
                    cents += centsPerKilogram.get(parts[0]) * weight;
                }

                @Override
                public void onError(Throwable t) {
                    response.onError(t);
                }

                @Override
                public void onCompleted() {
                    response.onNext(text(parcels + " parcels, " + kilograms + " kg, " + cents + " cents"));
                    response.onCompleted();
                }
            };
        }
    }

    /** Records the authority each call names and the kind of address it arrived from. */
    private final class CallerInterceptor implements ServerInterceptor {

        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
            SocketAddress remote = call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
            String origin = remote instanceof LocalAddress ? "local" : String.valueOf(remote);
            callers.add(call.getAuthority() + " from " + origin);
            return next.startCall(call, headers);
        }
    }
}
