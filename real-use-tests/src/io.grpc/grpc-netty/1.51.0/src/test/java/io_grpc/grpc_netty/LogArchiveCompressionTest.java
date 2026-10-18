/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty;

import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.StringValue;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import io.grpc.ClientStreamTracer;
import io.grpc.Codec;
import io.grpc.CompressorRegistry;
import io.grpc.DecompressorRegistry;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.grpc.ServerStreamTracer;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises per-message compression negotiated over the grpc-netty HTTP/2 transport, with
 * `CompressorRegistry` and `DecompressorRegistry` set on `NettyServerBuilder` and
 * `NettyChannelBuilder`.
 *
 * A log archive accepts gzip-compressed uploads chosen per call with
 * `CallOptions.withCompression`, and serves downloads that the server compresses with an
 * application-provided `deflate` codec through `ServerCallStreamObserver.setCompression`,
 * turning compression off with `setMessageCompression` for a segment that is already
 * compressed. Stream tracers on both ends report the wire and uncompressed sizes of each
 * call. A client that advertises no `grpc-accept-encoding` gets identity responses, an
 * upload in an encoding the server cannot decompress is refused UNIMPLEMENTED before the
 * handler runs, and a call naming a compressor the channel does not know never leaves it.
 */
class LogArchiveCompressionTest {

    private static final String SERVICE = "logs.Archive";

    private static final long WAIT_SECONDS = 10;

    private static final Metadata.Key<String> MESSAGE_ENCODING =
            Metadata.Key.of("grpc-encoding", Metadata.ASCII_STRING_MARSHALLER);

    private static final Metadata.Key<String> ACCEPT_ENCODING =
            Metadata.Key.of("grpc-accept-encoding", Metadata.ASCII_STRING_MARSHALLER);

    private static final MethodDescriptor<StringValue, StringValue> STORE =
            MethodDescriptor.<StringValue, StringValue>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE, "Store"))
                    .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .setResponseMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .build();

    private static final MethodDescriptor<StringValue, BytesValue> DOWNLOAD =
            MethodDescriptor.<StringValue, BytesValue>newBuilder()
                    .setType(MethodDescriptor.MethodType.SERVER_STREAMING)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE, "Download"))
                    .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .setResponseMarshaller(ProtoUtils.marshaller(BytesValue.getDefaultInstance()))
                    .build();

    private final Archive archive = new Archive();

    private final List<Metadata> requestHeaders = new CopyOnWriteArrayList<>();

    private final Map<String, SizeTally> serverInbound = new ConcurrentHashMap<>();

    private Server server;

    private ManagedChannel channel;

    @BeforeEach
    void startArchive() throws Exception {
        server = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
                .compressorRegistry(gzipAndDeflate())
                .decompressorRegistry(DecompressorRegistry.getDefaultInstance())
                .addStreamTracerFactory(new InboundSizeTracerFactory())
                .addService(ServerInterceptors.intercept(archive.bindService(), new HeaderRecorder()))
                .build()
                .start();
        channel = NettyChannelBuilder.forAddress("127.0.0.1", server.getPort())
                .usePlaintext()
                .compressorRegistry(gzipAndDeflate())
                .decompressorRegistry(DecompressorRegistry.getDefaultInstance().with(new DeflateCodec(), true))
                .build();
    }

    @AfterEach
    void stopArchive() throws Exception {
        channel.shutdown();
        server.shutdown();
        assertThat(channel.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        assertThat(server.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void storesAGzipCompressedUpload() {
        StringValue batch = text(logLines("checkout-api", 2_000));
        SizeTally client = new SizeTally();

        StringValue receipt = ClientCalls.blockingUnaryCall(channel, STORE, CallOptions.DEFAULT
                .withDeadlineAfter(WAIT_SECONDS, TimeUnit.SECONDS)
                .withCompression("gzip")
                .withStreamTracerFactory(client.factory()), batch);

        assertThat(receipt.getValue()).isEqualTo("stored 2000 lines from checkout-api");
        assertThat(archive.stored()).containsExactly(batch.getValue());
        assertThat(requestHeaders).singleElement().satisfies(headers -> {
            assertThat(headers.get(MESSAGE_ENCODING)).isEqualTo("gzip");
            assertThat(headers.get(ACCEPT_ENCODING)).contains("gzip", "deflate");
        });

        assertThat(client.outboundUncompressed.get()).isEqualTo(batch.getSerializedSize());
        assertThat(client.outboundWire.get()).isPositive().isLessThan(batch.getSerializedSize() / 10);
        SizeTally server = serverInbound.get(STORE.getFullMethodName());
        assertThat(server.inboundWire.get()).isEqualTo(client.outboundWire.get());
        assertThat(server.inboundUncompressed.get()).isEqualTo(batch.getSerializedSize());
    }

    @Test
    void downloadsDeflatedLogsAndARawPrecompressedSegment() {
        AtomicReference<Metadata> responseHeaders = new AtomicReference<>();
        Channel capturing = ClientInterceptors.intercept(channel,
                MetadataUtils.newCaptureMetadataInterceptor(responseHeaders, new AtomicReference<>()));
        SizeTally client = new SizeTally();

        List<BytesValue> segments = download(capturing, client, "checkout-api");

        BytesValue logs = Archive.textSegment("checkout-api");
        BytesValue snapshot = Archive.snapshotSegment();
        assertThat(segments).containsExactly(logs, snapshot);
        assertThat(responseHeaders.get().get(MESSAGE_ENCODING)).isEqualTo("deflate");

        long uncompressed = logs.getSerializedSize() + snapshot.getSerializedSize();
        assertThat(client.inboundUncompressed.get()).isEqualTo(uncompressed);
        assertThat(client.inboundWire.get())
                .isGreaterThanOrEqualTo(snapshot.getSerializedSize())
                .isLessThan(snapshot.getSerializedSize() + logs.getSerializedSize() / 10);
    }

    @Test
    void sendsIdentityToAClientThatAdvertisesNoEncodings() throws Exception {
        ManagedChannel legacy = NettyChannelBuilder.forAddress("127.0.0.1", server.getPort())
                .usePlaintext()
                .decompressorRegistry(DecompressorRegistry.emptyInstance().with(Codec.Identity.NONE, false))
                .build();
        try {
            AtomicReference<Metadata> responseHeaders = new AtomicReference<>();
            Channel capturing = ClientInterceptors.intercept(legacy,
                    MetadataUtils.newCaptureMetadataInterceptor(responseHeaders, new AtomicReference<>()));
            SizeTally client = new SizeTally();

            List<BytesValue> segments = download(capturing, client, "billing-worker");

            assertThat(segments).containsExactly(Archive.textSegment("billing-worker"), Archive.snapshotSegment());
            assertThat(requestHeaders).singleElement()
                    .satisfies(headers -> assertThat(headers.containsKey(ACCEPT_ENCODING)).isFalse());
            assertThat(responseHeaders.get().get(MESSAGE_ENCODING)).isEqualTo("identity");
            assertThat(client.inboundWire.get()).isEqualTo(client.inboundUncompressed.get());
        } finally {
            legacy.shutdown();
            assertThat(legacy.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void refusesUploadsInAnEncodingTheServerCannotDecompress() {
        StringValue batch = text(logLines("search-indexer", 50));

        assertThatThrownBy(() -> ClientCalls.blockingUnaryCall(channel, STORE, CallOptions.DEFAULT
                .withDeadlineAfter(WAIT_SECONDS, TimeUnit.SECONDS)
                .withCompression("deflate"), batch))
                .isInstanceOfSatisfying(StatusRuntimeException.class, e -> {
                    assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNIMPLEMENTED);
                    assertThat(e.getStatus().getDescription()).contains("deflate");
                });
        assertThat(requestHeaders).isEmpty();
        assertThat(archive.stored()).isEmpty();

        StringValue receipt = ClientCalls.blockingUnaryCall(channel, STORE, CallOptions.DEFAULT
                .withDeadlineAfter(WAIT_SECONDS, TimeUnit.SECONDS)
                .withCompression("gzip"), batch);
        assertThat(receipt.getValue()).isEqualTo("stored 50 lines from search-indexer");
    }

    @Test
    void failsACallNamingACompressorTheChannelDoesNotKnow() {
        StringValue batch = text(logLines("search-indexer", 5));

        assertThatThrownBy(() -> ClientCalls.blockingUnaryCall(channel, STORE, CallOptions.DEFAULT
                .withDeadlineAfter(WAIT_SECONDS, TimeUnit.SECONDS)
                .withCompression("br"), batch))
                .isInstanceOfSatisfying(StatusRuntimeException.class, e -> {
                    assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
                    assertThat(e.getStatus().getDescription()).contains("br");
                });
        assertThat(requestHeaders).isEmpty();
        assertThat(archive.stored()).isEmpty();
    }

    private static List<BytesValue> download(Channel target, SizeTally tally, String service) {
        Iterator<BytesValue> stream = ClientCalls.blockingServerStreamingCall(target, DOWNLOAD,
                CallOptions.DEFAULT
                        .withDeadlineAfter(WAIT_SECONDS, TimeUnit.SECONDS)
                        .withStreamTracerFactory(tally.factory()),
                text(service));
        List<BytesValue> segments = new ArrayList<>();
        stream.forEachRemaining(segments::add);
        return segments;
    }

    private static CompressorRegistry gzipAndDeflate() {
        CompressorRegistry compressors = CompressorRegistry.newEmptyInstance();
        compressors.register(new Codec.Gzip());
        compressors.register(new DeflateCodec());
        return compressors;
    }

    private static String logLines(String service, int count) {
        StringBuilder lines = new StringBuilder();
        for (int i = 0; i < count; i++) {
            lines.append("2026-10-18T09:00:00Z INFO ").append(service)
                    .append(" request handled status=200 path=/orders/").append(i % 7).append('\n');
        }
        return lines.toString();
    }

    private static StringValue text(String value) {
        return StringValue.newBuilder().setValue(value).build();
    }

    /** The log archive service, bound to its method descriptors by hand. */
    private static final class Archive {

        private static final int SNAPSHOT_BYTES = 16 * 1024;

        private final List<String> stored = new CopyOnWriteArrayList<>();

        ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder(SERVICE)
                    .addMethod(STORE, ServerCalls.asyncUnaryCall(this::store))
                    .addMethod(DOWNLOAD, ServerCalls.asyncServerStreamingCall(this::download))
                    .build();
        }

        List<String> stored() {
            return stored;
        }

        static BytesValue textSegment(String service) {
            return BytesValue.of(ByteString.copyFromUtf8(logLines(service, 1_000)));
        }

        /// A segment the archive already keeps gzip-compressed, so its bytes do not compress further.
        static BytesValue snapshotSegment() {
            byte[] bytes = new byte[SNAPSHOT_BYTES];
            new Random(9438L).nextBytes(bytes);
            return BytesValue.of(ByteString.copyFrom(bytes));
        }

        private void store(StringValue batch, StreamObserver<StringValue> response) {
            stored.add(batch.getValue());
            String service = batch.getValue().split(" ", 4)[2];
            long lines = batch.getValue().lines().count();
            response.onNext(text("stored " + lines + " lines from " + service));
            response.onCompleted();
        }

        private void download(StringValue service, StreamObserver<BytesValue> response) {
            ServerCallStreamObserver<BytesValue> call = (ServerCallStreamObserver<BytesValue>) response;
            call.setCompression("deflate");
            call.onNext(textSegment(service.getValue()));
            call.setMessageCompression(false);
            call.onNext(snapshotSegment());
            call.onCompleted();
        }
    }

    /** The `deflate` message encoding, implemented by the application on `java.util.zip`. */
    private static final class DeflateCodec implements Codec {

        @Override
        public String getMessageEncoding() {
            return "deflate";
        }

        @Override
        public OutputStream compress(OutputStream os) {
            return new DeflaterOutputStream(os);
        }

        @Override
        public InputStream decompress(InputStream is) {
            return new InflaterInputStream(is);
        }
    }

    /** Records the request headers of every call that reaches a handler. */
    private final class HeaderRecorder implements ServerInterceptor {

        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
            requestHeaders.add(headers);
            return next.startCall(call, headers);
        }
    }

    /** Sums the wire and uncompressed message sizes one stream reports to its tracer. */
    private static final class SizeTally {

        private final AtomicLong outboundWire = new AtomicLong();

        private final AtomicLong outboundUncompressed = new AtomicLong();

        private final AtomicLong inboundWire = new AtomicLong();

        private final AtomicLong inboundUncompressed = new AtomicLong();

        ClientStreamTracer.Factory factory() {
            return new ClientStreamTracer.Factory() {
                @Override
                public ClientStreamTracer newClientStreamTracer(
                        ClientStreamTracer.StreamInfo info, Metadata headers) {
                    return new ClientStreamTracer() {
                        @Override
                        public void outboundWireSize(long bytes) {
                            outboundWire.addAndGet(bytes);
                        }

                        @Override
                        public void outboundUncompressedSize(long bytes) {
                            outboundUncompressed.addAndGet(bytes);
                        }

                        @Override
                        public void inboundWireSize(long bytes) {
                            inboundWire.addAndGet(bytes);
                        }

                        @Override
                        public void inboundUncompressedSize(long bytes) {
                            inboundUncompressed.addAndGet(bytes);
                        }
                    };
                }
            };
        }
    }

    /** Tallies what the server reads off the wire for each method. */
    private final class InboundSizeTracerFactory extends ServerStreamTracer.Factory {

        @Override
        public ServerStreamTracer newServerStreamTracer(String fullMethodName, Metadata headers) {
            SizeTally tally = serverInbound.computeIfAbsent(fullMethodName, name -> new SizeTally());
            return new ServerStreamTracer() {
                @Override
                public void inboundWireSize(long bytes) {
                    tally.inboundWire.addAndGet(bytes);
                }

                @Override
                public void inboundUncompressedSize(long bytes) {
                    tally.inboundUncompressed.addAndGet(bytes);
                }
            };
        }
    }
}
