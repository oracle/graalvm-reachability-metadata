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
import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import io.grpc.ForwardingServerCall;
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
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Serves a stock-quote service over the grpc-netty HTTP/2 server and channel transport,
 * configured through `NettyServerBuilder` and `NettyChannelBuilder` on a loopback port.
 *
 * The service is defined without code generation: protobuf well-known types are marshalled
 * with `ProtoUtils` and the handlers are bound with `ServerCalls`, the way an application
 * wires a small service by hand. A trading desk looks up prices, watches a ticker stream,
 * places orders over a bidirectional stream, and has the channel's inbound message-size
 * limit refuse an oversized report, while a desk identifier travels in request headers and is echoed back.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StockQuoteNettyTransportTest {

    private static final String SERVICE = "markets.StockQuotes";

    private static final int MAX_INBOUND_BYTES = 4 * 1024;

    private static final Metadata.Key<String> DESK_ID =
            Metadata.Key.of("x-desk-id", Metadata.ASCII_STRING_MARSHALLER);

    private static final Metadata.Key<String> SERVED_BY =
            Metadata.Key.of("x-served-by", Metadata.ASCII_STRING_MARSHALLER);

    private static final MethodDescriptor<StringValue, Int64Value> LOOKUP =
            MethodDescriptor.<StringValue, Int64Value>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE, "Lookup"))
                    .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .setResponseMarshaller(ProtoUtils.marshaller(Int64Value.getDefaultInstance()))
                    .build();

    private static final MethodDescriptor<StringValue, Int64Value> WATCH =
            MethodDescriptor.<StringValue, Int64Value>newBuilder()
                    .setType(MethodDescriptor.MethodType.SERVER_STREAMING)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE, "Watch"))
                    .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .setResponseMarshaller(ProtoUtils.marshaller(Int64Value.getDefaultInstance()))
                    .build();

    private static final MethodDescriptor<StringValue, StringValue> TRADE =
            MethodDescriptor.<StringValue, StringValue>newBuilder()
                    .setType(MethodDescriptor.MethodType.BIDI_STREAMING)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE, "Trade"))
                    .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .setResponseMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .build();

    private static final MethodDescriptor<StringValue, StringValue> REPORT =
            MethodDescriptor.<StringValue, StringValue>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE, "Report"))
                    .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .setResponseMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .build();

    private final QuoteBook book = new QuoteBook(Map.of("ACME", 12_550L, "GLOBEX", 8_020L));

    private final List<String> desksSeen = new CopyOnWriteArrayList<>();

    private Server server;

    private ManagedChannel channel;

    @BeforeAll
    void startServerAndChannel() throws Exception {
        server = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
                .keepAliveTime(30, TimeUnit.SECONDS)
                .addService(ServerInterceptors.intercept(book.bindService(), new DeskHeaderInterceptor()))
                .build()
                .start();
        channel = NettyChannelBuilder.forAddress("127.0.0.1", server.getPort())
                .usePlaintext()
                .userAgent("trading-desk/1.0")
                .maxInboundMessageSize(MAX_INBOUND_BYTES)
                .build();
    }

    @AfterAll
    void stopServerAndChannel() throws Exception {
        channel.shutdown();
        server.shutdown();
        assertThat(channel.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(server.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void looksUpPricesAndEchoesTheDeskHeader() {
        Metadata headers = new Metadata();
        headers.put(DESK_ID, "desk-emea-3");
        AtomicReference<Metadata> responseHeaders = new AtomicReference<>();
        AtomicReference<Metadata> trailers = new AtomicReference<>();
        Channel deskChannel = ClientInterceptors.intercept(channel,
                MetadataUtils.newAttachHeadersInterceptor(headers),
                MetadataUtils.newCaptureMetadataInterceptor(responseHeaders, trailers));

        Int64Value price = ClientCalls.blockingUnaryCall(deskChannel, LOOKUP,
                CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS), text("ACME"));

        assertThat(price.getValue()).isEqualTo(12_550L);
        assertThat(responseHeaders.get().get(SERVED_BY)).isEqualTo("quotes-1");
        assertThat(desksSeen).contains("desk-emea-3");

        assertThatThrownBy(() -> ClientCalls.blockingUnaryCall(deskChannel, LOOKUP,
                CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS), text("INITECH")))
                .isInstanceOfSatisfying(StatusRuntimeException.class, e -> {
                    assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
                    assertThat(e.getStatus().getDescription()).isEqualTo("unknown symbol INITECH");
                });
    }

    @Test
    void streamsTickerUpdatesForASymbol() {
        Iterator<Int64Value> ticks = ClientCalls.blockingServerStreamingCall(channel, WATCH,
                CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS), text("GLOBEX"));

        List<Long> prices = new ArrayList<>();
        ticks.forEachRemaining(tick -> prices.add(tick.getValue()));

        assertThat(prices).containsExactly(8_020L, 8_045L, 8_070L, 8_095L);
    }

    @Test
    void confirmsOrdersOverABidirectionalStream() throws Exception {
        List<String> confirmations = new CopyOnWriteArrayList<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch completed = new CountDownLatch(1);

        StreamObserver<StringValue> orders = ClientCalls.asyncBidiStreamingCall(
                channel.newCall(TRADE, CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS)),
                new StreamObserver<StringValue>() {
                    @Override
                    public void onNext(StringValue confirmation) {
                        confirmations.add(confirmation.getValue());
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
        orders.onNext(text("BUY 100 ACME"));
        orders.onNext(text("SELL 40 GLOBEX"));
        orders.onNext(text("BUY 10 INITECH"));
        orders.onCompleted();

        assertThat(completed.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(failure.get()).isNull();
        assertThat(confirmations).containsExactly(
                "FILLED BUY 100 ACME @ 12550",
                "FILLED SELL 40 GLOBEX @ 8020",
                "REJECTED BUY 10 INITECH: unknown symbol",
                "SESSION CLOSED: 2 filled, 1 rejected");
    }

    @Test
    void refusesReportsLargerThanTheChannelLimit() {
        StringValue shortReport = ClientCalls.blockingUnaryCall(channel, REPORT,
                CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS), text("ACME 2"));
        assertThat(shortReport.getValue()).isEqualTo("ACME day 1: 12550\nACME day 2: 12575\n");

        assertThatThrownBy(() -> ClientCalls.blockingUnaryCall(channel, REPORT,
                CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS), text("ACME 1000")))
                .isInstanceOfSatisfying(StatusRuntimeException.class, e ->
                        assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.RESOURCE_EXHAUSTED));

        Int64Value price = ClientCalls.blockingUnaryCall(channel, LOOKUP,
                CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS), text("GLOBEX"));
        assertThat(price.getValue()).isEqualTo(8_020L);
    }

    private static StringValue text(String value) {
        return StringValue.newBuilder().setValue(value).build();
    }

    /** The quote service implementation, bound to its method descriptors by hand. */
    private static final class QuoteBook {

        private static final long TICK_CENTS = 25L;

        private static final int TICKS = 4;

        private final Map<String, Long> prices;

        QuoteBook(Map<String, Long> prices) {
            this.prices = prices;
        }

        ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder(SERVICE)
                    .addMethod(LOOKUP, ServerCalls.asyncUnaryCall(this::lookup))
                    .addMethod(WATCH, ServerCalls.asyncServerStreamingCall(this::watch))
                    .addMethod(TRADE, ServerCalls.asyncBidiStreamingCall(this::trade))
                    .addMethod(REPORT, ServerCalls.asyncUnaryCall(this::report))
                    .build();
        }

        private void lookup(StringValue symbol, StreamObserver<Int64Value> response) {
            Long price = prices.get(symbol.getValue());
            if (price == null) {
                response.onError(Status.NOT_FOUND
                        .withDescription("unknown symbol " + symbol.getValue())
                        .asRuntimeException());
                return;
            }
            response.onNext(Int64Value.of(price));
            response.onCompleted();
        }

        private void watch(StringValue symbol, StreamObserver<Int64Value> response) {
            long price = prices.get(symbol.getValue());
            for (int i = 0; i < TICKS; i++) {
                response.onNext(Int64Value.of(price + i * TICK_CENTS));
            }
            response.onCompleted();
        }

        private void report(StringValue request, StreamObserver<StringValue> response) {
            String[] parts = request.getValue().split(" ");
            long price = prices.get(parts[0]);
            int days = Integer.parseInt(parts[1]);
            StringBuilder report = new StringBuilder();
            for (int day = 0; day < days; day++) {
                report.append(parts[0]).append(" day ").append(day + 1).append(": ")
                        .append(price + day * TICK_CENTS).append('\n');
            }
            response.onNext(text(report.toString()));
            response.onCompleted();
        }

        private StreamObserver<StringValue> trade(StreamObserver<StringValue> confirmations) {
            return new StreamObserver<StringValue>() {
                private int filled;

                private int rejected;

                @Override
                public void onNext(StringValue order) {
                    String[] parts = order.getValue().split(" ");
                    Long price = prices.get(parts[2]);
                    if (price == null) {
                        rejected++;
                        confirmations.onNext(text("REJECTED " + order.getValue() + ": unknown symbol"));
                    } else {
                        filled++;
                        confirmations.onNext(text("FILLED " + order.getValue() + " @ " + price));
                    }
                }

                @Override
                public void onError(Throwable t) {
                    confirmations.onError(t);
                }

                @Override
                public void onCompleted() {
                    confirmations.onNext(text("SESSION CLOSED: " + filled + " filled, " + rejected + " rejected"));
                    confirmations.onCompleted();
                }
            };
        }
    }

    /** Records the calling desk and stamps every response with the serving node. */
    private final class DeskHeaderInterceptor implements ServerInterceptor {

        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
            String desk = headers.get(DESK_ID);
            if (desk != null) {
                desksSeen.add(desk);
            }
            return next.startCall(new ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {
                @Override
                public void sendHeaders(Metadata responseHeaders) {
                    responseHeaders.put(SERVED_BY, "quotes-1");
                    super.sendHeaders(responseHeaders);
                }
            }, headers);
        }
    }
}
