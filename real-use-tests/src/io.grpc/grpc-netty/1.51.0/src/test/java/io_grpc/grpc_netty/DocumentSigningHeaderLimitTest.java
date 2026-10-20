/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty;

import com.google.protobuf.StringValue;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import io.grpc.Context;
import io.grpc.Contexts;
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
import io.netty.handler.codec.http2.Http2Exception;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the HTTP/2 header-list size limits of the grpc-netty transport, set with
 * `maxInboundMetadataSize` on `NettyServerBuilder` and `NettyChannelBuilder`.
 *
 * A document-signing service receives a certificate chain as binary (`-bin`) request
 * metadata, which the Netty transport carries base64-encoded in HTTP/2 headers, and
 * returns the signature in binary trailers. Each peer advertises its limit as the HTTP/2
 * `SETTINGS_MAX_HEADER_LIST_SIZE`, so a request whose headers exceed the server's limit is
 * refused by the client's HPACK encoder before the handler runs, trailers that exceed the
 * client's limit reset the stream after the handler ran, and both refusals fail only the
 * offending stream while the connection keeps serving calls.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DocumentSigningHeaderLimitTest {

    private static final String SERVICE = "signing.DocumentSigner";

    private static final int SERVER_METADATA_LIMIT = 4 * 1024;

    private static final int CLIENT_METADATA_LIMIT = 2 * 1024;

    private static final Metadata.Key<byte[]> CERT_CHAIN =
            Metadata.Key.of("x-cert-chain-bin", Metadata.BINARY_BYTE_MARSHALLER);

    private static final Metadata.Key<byte[]> SIGNATURE =
            Metadata.Key.of("x-signature-bin", Metadata.BINARY_BYTE_MARSHALLER);

    private static final Metadata.Key<String> AUDIT_TRAIL =
            Metadata.Key.of("x-audit-trail", Metadata.ASCII_STRING_MARSHALLER);

    private static final MethodDescriptor<StringValue, StringValue> SIGN =
            MethodDescriptor.<StringValue, StringValue>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE, "Sign"))
                    .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .setResponseMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .build();

    private final Signer signer = new Signer();

    private Server server;

    private ManagedChannel channel;

    @BeforeAll
    void startServerAndChannel() throws Exception {
        server = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
                .maxInboundMetadataSize(SERVER_METADATA_LIMIT)
                .addService(ServerInterceptors.intercept(signer.bindService(), new SigningContextInterceptor()))
                .build()
                .start();
        channel = NettyChannelBuilder.forAddress("127.0.0.1", server.getPort())
                .usePlaintext()
                .maxInboundMetadataSize(CLIENT_METADATA_LIMIT)
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
    void signsDocumentWithBinaryCertificateChainInHeaders() {
        byte[] chain = certificateChain(1_024);
        AtomicReference<Metadata> trailers = new AtomicReference<>();

        StringValue receipt = ClientCalls.blockingUnaryCall(signingChannel(chain, trailers), SIGN,
                callOptions(), text("contract-17.pdf"));

        assertThat(receipt.getValue()).isEqualTo("signed contract-17.pdf with chain of 1024 bytes");
        assertThat(trailers.get().get(SIGNATURE)).isEqualTo(signature("contract-17.pdf", chain));
        assertThat(signer.chainsSeen).contains(HexFormat.of().formatHex(sha256(chain)));
    }

    @Test
    void requestHeadersOverTheServerLimitNeverReachTheHandler() {
        assertThatSigningStillWorks("before-oversized-request.pdf");
        byte[] chain = certificateChain(SERVER_METADATA_LIMIT * 2);

        assertThatThrownBy(() -> ClientCalls.blockingUnaryCall(signingChannel(chain, new AtomicReference<>()),
                SIGN, callOptions(), text("oversized-chain.pdf")))
                .isInstanceOfSatisfying(StatusRuntimeException.class, e -> {
                    assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
                    assertThat(e.getStatus().getCause())
                            .isInstanceOf(Http2Exception.HeaderListSizeException.class)
                            .hasMessageContaining("max allowed size (" + SERVER_METADATA_LIMIT + ")");
                });

        assertThat(signer.documentsSeen).doesNotContain("oversized-chain.pdf");
        assertThatSigningStillWorks("after-oversized-request.pdf");
    }

    @Test
    void trailersOverTheClientLimitResetOnlyThatStream() {
        assertThatThrownBy(() -> ClientCalls.blockingUnaryCall(channel, SIGN, callOptions(),
                text("audited-merger.pdf")))
                .isInstanceOfSatisfying(StatusRuntimeException.class, e -> {
                    assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
                    assertThat(e.getStatus().getDescription())
                            .isEqualTo("RST_STREAM closed stream. HTTP/2 error code: PROTOCOL_ERROR");
                });

        assertThat(signer.documentsSeen).contains("audited-merger.pdf");
        assertThatSigningStillWorks("after-oversized-trailers.pdf");
    }

    private void assertThatSigningStillWorks(String document) {
        byte[] chain = certificateChain(256);
        AtomicReference<Metadata> trailers = new AtomicReference<>();
        StringValue receipt = ClientCalls.blockingUnaryCall(signingChannel(chain, trailers), SIGN,
                callOptions(), text(document));
        assertThat(receipt.getValue()).isEqualTo("signed " + document + " with chain of 256 bytes");
        assertThat(trailers.get().get(SIGNATURE)).isEqualTo(signature(document, chain));
    }

    private Channel signingChannel(byte[] chain, AtomicReference<Metadata> trailers) {
        Metadata headers = new Metadata();
        headers.put(CERT_CHAIN, chain);
        return ClientInterceptors.intercept(channel,
                MetadataUtils.newAttachHeadersInterceptor(headers),
                MetadataUtils.newCaptureMetadataInterceptor(new AtomicReference<>(), trailers));
    }

    private static CallOptions callOptions() {
        return CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS);
    }

    private static byte[] certificateChain(int length) {
        byte[] chain = new byte[length];
        for (int i = 0; i < length; i++) {
            chain[i] = (byte) (i * 31 + 7);
        }
        return chain;
    }

    private static byte[] signature(String document, byte[] chain) {
        byte[] name = document.getBytes(StandardCharsets.UTF_8);
        byte[] signed = Arrays.copyOf(name, name.length + chain.length);
        System.arraycopy(chain, 0, signed, name.length, chain.length);
        return sha256(signed);
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static StringValue text(String value) {
        return StringValue.newBuilder().setValue(value).build();
    }

    /**
     * Exposes the request's certificate chain to the handler through the gRPC `Context` and
     * appends the trailers the handler collected when the call closes.
     */
    private static final class SigningContextInterceptor implements ServerInterceptor {

        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                Metadata headers, ServerCallHandler<ReqT, RespT> next) {
            byte[] chain = headers.get(CERT_CHAIN);
            Metadata extraTrailers = new Metadata();
            Context context = Context.current()
                    .withValue(Signer.CHAIN, chain == null ? new byte[0] : chain)
                    .withValue(Signer.TRAILERS, extraTrailers);
            ServerCall<ReqT, RespT> trailingCall = new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
                @Override
                public void close(Status status, Metadata trailers) {
                    trailers.merge(extraTrailers);
                    super.close(status, trailers);
                }
            };
            return Contexts.interceptCall(context, trailingCall, headers, next);
        }
    }

    /**
     * Signs documents with the certificate chain from the request headers and returns the
     * signature in the trailers. A document whose name starts with `audited-` instead gets an
     * audit trail in its trailers that is larger than the client accepts.
     */
    private static final class Signer {

        static final Context.Key<byte[]> CHAIN = Context.key("cert-chain");

        static final Context.Key<Metadata> TRAILERS = Context.key("signing-trailers");

        private final List<String> documentsSeen = new CopyOnWriteArrayList<>();

        private final List<String> chainsSeen = new CopyOnWriteArrayList<>();

        ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder(SERVICE)
                    .addMethod(SIGN, ServerCalls.asyncUnaryCall(this::sign))
                    .build();
        }

        private void sign(StringValue document, StreamObserver<StringValue> response) {
            String name = document.getValue();
            documentsSeen.add(name);
            Metadata trailers = TRAILERS.get();
            if (name.startsWith("audited-")) {
                trailers.put(AUDIT_TRAIL, "reviewed;".repeat(CLIENT_METADATA_LIMIT / 4));
                response.onNext(text("signed " + name + " with audit trail"));
                response.onCompleted();
                return;
            }
            byte[] chain = CHAIN.get();
            chainsSeen.add(HexFormat.of().formatHex(sha256(chain)));
            trailers.put(SIGNATURE, signature(name, chain));
            response.onNext(text("signed " + name + " with chain of " + chain.length + " bytes"));
            response.onCompleted();
        }
    }
}
