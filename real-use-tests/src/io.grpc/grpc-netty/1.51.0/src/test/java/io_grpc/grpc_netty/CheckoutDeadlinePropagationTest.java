/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty;

import com.google.protobuf.Int32Value;
import com.google.protobuf.StringValue;
import io.grpc.CallOptions;
import io.grpc.Context;
import io.grpc.Deadline;
import io.grpc.ForwardingServerCallListener;
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
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises call deadlines carried over the grpc-netty HTTP/2 transport, encoded by
 * `NettyChannelBuilder` channels as the `grpc-timeout` request header and turned back into
 * a server `Context` deadline by `NettyServerBuilder` servers.
 *
 * A storefront checkout service runs on one Netty server and calls an inventory service on
 * a second Netty server through its own Netty channel. The caller's deadline flows from the
 * shopper's call into the checkout handler's `Context` and from there, untouched, into the
 * inventory call; a deadline that runs out cancels the held inventory call and fails the
 * shopper with `DEADLINE_EXCEEDED`; a call started after its deadline never leaves the
 * client; and checkout caps its own inventory budget to fall back to a cached estimate.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CheckoutDeadlinePropagationTest {

    private static final Metadata.Key<String> GRPC_TIMEOUT =
            Metadata.Key.of("grpc-timeout", Metadata.ASCII_STRING_MARSHALLER);

    private static final MethodDescriptor<StringValue, StringValue> RESERVE =
            MethodDescriptor.<StringValue, StringValue>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName("storefront.Checkout", "Reserve"))
                    .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .setResponseMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .build();

    private static final MethodDescriptor<StringValue, StringValue> ESTIMATE =
            MethodDescriptor.<StringValue, StringValue>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName("storefront.Checkout", "Estimate"))
                    .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .setResponseMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .build();

    private static final MethodDescriptor<StringValue, Int32Value> HOLD =
            MethodDescriptor.<StringValue, Int32Value>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName("inventory.Stock", "Hold"))
                    .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .setResponseMarshaller(ProtoUtils.marshaller(Int32Value.getDefaultInstance()))
                    .build();

    private static final long ESTIMATE_BUDGET_MILLIS = 200;

    private final CallLog log = new CallLog();

    private Server inventoryServer;

    private Server checkoutServer;

    private ManagedChannel inventoryChannel;

    private ManagedChannel shopperChannel;

    @BeforeAll
    void startServices() throws Exception {
        inventoryServer = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
                .addService(ServerInterceptors.intercept(new Inventory(log).bindService(),
                        new TimeoutHeaderRecorder(log, "inventory")))
                .build()
                .start();
        inventoryChannel = NettyChannelBuilder.forAddress("127.0.0.1", inventoryServer.getPort())
                .usePlaintext()
                .build();
        checkoutServer = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
                .addService(ServerInterceptors.intercept(new Checkout(log, inventoryChannel).bindService(),
                        new TimeoutHeaderRecorder(log, "checkout")))
                .build()
                .start();
        shopperChannel = NettyChannelBuilder.forAddress("127.0.0.1", checkoutServer.getPort())
                .usePlaintext()
                .build();
    }

    @AfterAll
    void stopServices() throws Exception {
        shopperChannel.shutdown();
        checkoutServer.shutdown();
        inventoryChannel.shutdown();
        inventoryServer.shutdown();
        assertThat(shopperChannel.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(checkoutServer.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(inventoryChannel.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(inventoryServer.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void propagatesTheShopperDeadlineThroughCheckoutToInventory() {
        StringValue reply = ClientCalls.blockingUnaryCall(shopperChannel, RESERVE,
                CallOptions.DEFAULT.withDeadlineAfter(5, TimeUnit.SECONDS), text("sku-lamp"));

        assertThat(reply.getValue()).isEqualTo("reserved sku-lamp, 7 left");

        long checkoutTimeout = log.headerTimeoutNanos("checkout", "sku-lamp").orElseThrow();
        long inventoryTimeout = log.headerTimeoutNanos("inventory", "sku-lamp").orElseThrow();
        assertThat(checkoutTimeout).isPositive().isLessThanOrEqualTo(TimeUnit.SECONDS.toNanos(5));
        assertThat(inventoryTimeout).isPositive().isLessThanOrEqualTo(checkoutTimeout);

        long checkoutRemaining = log.contextDeadlineNanos("checkout", "sku-lamp").orElseThrow();
        long inventoryRemaining = log.contextDeadlineNanos("inventory", "sku-lamp").orElseThrow();
        assertThat(checkoutRemaining).isPositive().isLessThanOrEqualTo(checkoutTimeout);
        assertThat(inventoryRemaining).isPositive().isLessThanOrEqualTo(inventoryTimeout);
    }

    @Test
    void sendsNoTimeoutWhenTheShopperSetsNoDeadline() {
        StringValue reply = ClientCalls.blockingUnaryCall(shopperChannel, RESERVE,
                CallOptions.DEFAULT, text("sku-rug"));

        assertThat(reply.getValue()).isEqualTo("reserved sku-rug, 2 left");
        assertThat(log.sawCall("checkout", "sku-rug")).isTrue();
        assertThat(log.headerTimeoutNanos("checkout", "sku-rug")).isEmpty();
        assertThat(log.contextDeadlineNanos("checkout", "sku-rug")).isEmpty();
        assertThat(log.headerTimeoutNanos("inventory", "sku-rug")).isEmpty();
        assertThat(log.contextDeadlineNanos("inventory", "sku-rug")).isEmpty();
    }

    @Test
    void expiredShopperDeadlineCancelsTheHeldInventoryCall() throws Exception {
        assertThatThrownBy(() -> ClientCalls.blockingUnaryCall(shopperChannel, RESERVE,
                CallOptions.DEFAULT.withDeadlineAfter(400, TimeUnit.MILLISECONDS), text("slow-sofa")))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.DEADLINE_EXCEEDED));

        assertThat(log.inventoryCancelled("slow-sofa").await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(log.checkoutFailure("slow-sofa").await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(log.checkoutFailureCode("slow-sofa"))
                .isIn(Status.Code.DEADLINE_EXCEEDED, Status.Code.CANCELLED);
        assertThat(log.headerTimeoutNanos("inventory", "slow-sofa").orElseThrow())
                .isPositive()
                .isLessThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(400));
    }

    @Test
    void callStartedAfterItsDeadlineNeverReachesCheckout() {
        int callsBefore = log.checkoutCalls();

        assertThatThrownBy(() -> ClientCalls.blockingUnaryCall(shopperChannel, RESERVE,
                CallOptions.DEFAULT.withDeadline(Deadline.after(-1, TimeUnit.SECONDS)), text("sku-late")))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.DEADLINE_EXCEEDED));

        assertThat(log.checkoutCalls()).isEqualTo(callsBefore);
        assertThat(log.sawCall("checkout", "sku-late")).isFalse();
    }

    @Test
    void checkoutCapsItsInventoryBudgetAndFallsBackToACachedEstimate() throws Exception {
        StringValue reply = ClientCalls.blockingUnaryCall(shopperChannel, ESTIMATE,
                CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS), text("slow-desk"));

        assertThat(reply.getValue()).isEqualTo("slow-desk: usually ships in 3 days");
        assertThat(log.inventoryCancelled("slow-desk").await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(log.headerTimeoutNanos("checkout", "slow-desk").orElseThrow())
                .isGreaterThan(TimeUnit.MILLISECONDS.toNanos(ESTIMATE_BUDGET_MILLIS));
        assertThat(log.headerTimeoutNanos("inventory", "slow-desk").orElseThrow())
                .isPositive()
                .isLessThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(ESTIMATE_BUDGET_MILLIS));

        StringValue live = ClientCalls.blockingUnaryCall(shopperChannel, ESTIMATE,
                CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS), text("sku-lamp"));

        assertThat(live.getValue()).isEqualTo("sku-lamp: 7 in stock, ships today");
    }

    private static StringValue text(String value) {
        return StringValue.newBuilder().setValue(value).build();
    }

    /** What each service saw of a call, keyed by service name and SKU. */
    private static final class CallLog {

        private static final Pattern TIMEOUT = Pattern.compile("(\\d{1,8})([HMSmun])");

        private final Map<String, String> rawTimeouts = new ConcurrentHashMap<>();

        private final Map<String, Long> remainingNanos = new ConcurrentHashMap<>();

        private final Map<String, Boolean> calls = new ConcurrentHashMap<>();

        private final Map<String, CountDownLatch> inventoryCancellations = new ConcurrentHashMap<>();

        private final Map<String, CountDownLatch> checkoutFailures = new ConcurrentHashMap<>();

        private final Map<String, Status.Code> checkoutFailureCodes = new ConcurrentHashMap<>();

        private final AtomicInteger checkoutCalls = new AtomicInteger();

        void recordHeader(String service, String sku, String rawTimeout) {
            if (rawTimeout != null) {
                rawTimeouts.put(service + "/" + sku, rawTimeout);
            }
        }

        void recordCall(String service, String sku) {
            calls.put(service + "/" + sku, Boolean.TRUE);
            if (service.equals("checkout")) {
                checkoutCalls.incrementAndGet();
            }
            Deadline deadline = Context.current().getDeadline();
            if (deadline != null) {
                remainingNanos.put(service + "/" + sku, deadline.timeRemaining(TimeUnit.NANOSECONDS));
            }
        }

        boolean sawCall(String service, String sku) {
            return calls.containsKey(service + "/" + sku);
        }

        int checkoutCalls() {
            return checkoutCalls.get();
        }

        Optional<Long> headerTimeoutNanos(String service, String sku) {
            String raw = rawTimeouts.get(service + "/" + sku);
            if (raw == null) {
                return Optional.empty();
            }
            Matcher matcher = TIMEOUT.matcher(raw);
            assertThat(matcher.matches()).as("grpc-timeout %s", raw).isTrue();
            long amount = Long.parseLong(matcher.group(1));
            TimeUnit unit = switch (matcher.group(2)) {
                case "H" -> TimeUnit.HOURS;
                case "M" -> TimeUnit.MINUTES;
                case "S" -> TimeUnit.SECONDS;
                case "m" -> TimeUnit.MILLISECONDS;
                case "u" -> TimeUnit.MICROSECONDS;
                default -> TimeUnit.NANOSECONDS;
            };
            return Optional.of(unit.toNanos(amount));
        }

        Optional<Long> contextDeadlineNanos(String service, String sku) {
            return Optional.ofNullable(remainingNanos.get(service + "/" + sku));
        }

        CountDownLatch inventoryCancelled(String sku) {
            return inventoryCancellations.computeIfAbsent(sku, key -> new CountDownLatch(1));
        }

        CountDownLatch checkoutFailure(String sku) {
            return checkoutFailures.computeIfAbsent(sku, key -> new CountDownLatch(1));
        }

        void recordCheckoutFailure(String sku, Status.Code code) {
            checkoutFailureCodes.put(sku, code);
            checkoutFailure(sku).countDown();
        }

        Status.Code checkoutFailureCode(String sku) {
            return checkoutFailureCodes.get(sku);
        }
    }

    /** Records the raw `grpc-timeout` request header each service receives. */
    private static final class TimeoutHeaderRecorder implements ServerInterceptor {

        private final CallLog log;

        private final String service;

        TimeoutHeaderRecorder(CallLog log, String service) {
            this.log = log;
            this.service = service;
        }

        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                Metadata headers, ServerCallHandler<ReqT, RespT> next) {
            String rawTimeout = headers.get(GRPC_TIMEOUT);
            ServerCall.Listener<ReqT> delegate = next.startCall(call, headers);
            return new ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT>(delegate) {
                @Override
                public void onMessage(ReqT message) {
                    log.recordHeader(service, ((StringValue) message).getValue(), rawTimeout);
                    super.onMessage(message);
                }
            };
        }
    }

    /** Inventory backend: answers stock counts and holds `slow-` SKUs until the call is cancelled. */
    private static final class Inventory {

        private final Map<String, Integer> stock = Map.of("sku-lamp", 7, "sku-rug", 2);

        private final CallLog log;

        Inventory(CallLog log) {
            this.log = log;
        }

        ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder("inventory.Stock")
                    .addMethod(HOLD, ServerCalls.asyncUnaryCall(this::hold))
                    .build();
        }

        private void hold(StringValue request, StreamObserver<Int32Value> responses) {
            String sku = request.getValue();
            log.recordCall("inventory", sku);
            if (sku.startsWith("slow-")) {
                CountDownLatch cancelled = log.inventoryCancelled(sku);
                Context.current().addListener(context -> cancelled.countDown(), Runnable::run);
                return;
            }
            responses.onNext(Int32Value.newBuilder().setValue(stock.getOrDefault(sku, 0)).build());
            responses.onCompleted();
        }
    }

    /** Checkout frontend: reserves stock through the inventory channel under the caller's deadline. */
    private static final class Checkout {

        private final CallLog log;

        private final ManagedChannel inventory;

        Checkout(CallLog log, ManagedChannel inventory) {
            this.log = log;
            this.inventory = inventory;
        }

        ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder("storefront.Checkout")
                    .addMethod(RESERVE, ServerCalls.asyncUnaryCall(this::reserve))
                    .addMethod(ESTIMATE, ServerCalls.asyncUnaryCall(this::estimate))
                    .build();
        }

        private void reserve(StringValue request, StreamObserver<StringValue> responses) {
            String sku = request.getValue();
            log.recordCall("checkout", sku);
            try {
                Int32Value left = ClientCalls.blockingUnaryCall(inventory, HOLD, CallOptions.DEFAULT, request);
                responses.onNext(text("reserved " + sku + ", " + left.getValue() + " left"));
                responses.onCompleted();
            } catch (StatusRuntimeException e) {
                log.recordCheckoutFailure(sku, e.getStatus().getCode());
                responses.onError(e.getStatus().asRuntimeException());
            }
        }

        private void estimate(StringValue request, StreamObserver<StringValue> responses) {
            String sku = request.getValue();
            log.recordCall("checkout", sku);
            String answer;
            try {
                Int32Value left = ClientCalls.blockingUnaryCall(inventory, HOLD,
                        CallOptions.DEFAULT.withDeadlineAfter(ESTIMATE_BUDGET_MILLIS, TimeUnit.MILLISECONDS), request);
                answer = sku + ": " + left.getValue() + " in stock, ships today";
            } catch (StatusRuntimeException e) {
                assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.DEADLINE_EXCEEDED);
                answer = sku + ": usually ships in 3 days";
            }
            responses.onNext(text(answer));
            responses.onCompleted();
        }
    }
}
