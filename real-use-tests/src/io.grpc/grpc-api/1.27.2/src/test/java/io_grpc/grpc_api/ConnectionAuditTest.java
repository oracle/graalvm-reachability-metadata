/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_api;

import com.google.protobuf.StringValue;
import io.grpc.Attributes;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import io.grpc.ForwardingServerCall.SimpleForwardingServerCall;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.grpc.ServerTransportFilter;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises gRPC's server transport-filter subsystem to audit catalog requests by client connection.
public class ConnectionAuditTest {
    private static final Attributes.Key<AuditSession> SESSION = Attributes.Key.create("catalog-audit-session");
    private static final Metadata.Key<String> SESSION_HEADER =
            Metadata.Key.of("audit-session", Metadata.ASCII_STRING_MARSHALLER);
    private static final String SERVICE_NAME = "catalog.AuditedCatalog";
    private static final MethodDescriptor<StringValue, StringValue> LOOKUP =
            MethodDescriptor.<StringValue, StringValue>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, "Lookup"))
                    .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .setResponseMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .build();

    @Test
    @Timeout(60)
    public void correlatesRequestsAndFinalizesTheAuditWhenTheConnectionCloses() throws Exception {
        ConnectionAuditor auditor = new ConnectionAuditor();
        CatalogService catalog = new CatalogService(Map.of("NOTEBOOK-42", "Notebook", "PENCIL-7", "Pencil"));
        Server server = ServerBuilder.forPort(0)
                .addTransportFilter(auditor)
                .addService(ServerInterceptors.intercept(catalog.bindService(), auditor))
                .build().start();
        ManagedChannel channel = null;
        try {
            channel = ManagedChannelBuilder.forAddress("127.0.0.1", server.getPort())
                    .usePlaintext()
                    .build();
            AtomicReference<Metadata> headers = new AtomicReference<>();
            AtomicReference<Metadata> trailers = new AtomicReference<>();
            Channel client = ClientInterceptors.intercept(channel,
                    MetadataUtils.newCaptureMetadataInterceptor(headers, trailers));

            assertEquals("Notebook", lookup(client, "NOTEBOOK-42"));
            String sessionId = headers.get().get(SESSION_HEADER);
            assertNotNull(sessionId, "Catalog response must identify its audit session");
            assertEquals("Pencil", lookup(client, "PENCIL-7"));
            assertEquals(sessionId, headers.get().get(SESSION_HEADER));

            AuditSession session = auditor.active.get(sessionId);
            assertNotNull(session);
            assertTrue(session.peer.getAddress().isLoopbackAddress(), "Audit must capture the actual client peer");
            assertEquals(2, session.requests.get());
            assertEquals(1, auditor.active.size(), "Both requests must share a connection audit");

            // Closing the application channel must flush the connection's final audit record.
            channel.shutdown();
            assertTrue(channel.awaitTermination(10, TimeUnit.SECONDS), "Client channel did not terminate");
            assertEquals(new CompletedAudit(sessionId, 2), auditor.completed.poll(10, TimeUnit.SECONDS));
            assertTrue(auditor.active.isEmpty(), "Disconnected client must not retain an active audit session");
        } finally {
            if (channel != null) {
                channel.shutdownNow();
            }
            server.shutdownNow();
            if (channel != null) {
                assertTrue(channel.awaitTermination(10, TimeUnit.SECONDS), "Client channel did not terminate");
            }
            assertTrue(server.awaitTermination(10, TimeUnit.SECONDS), "Catalog server did not terminate");
        }
    }

    private static String lookup(Channel channel, String sku) {
        return ClientCalls.blockingUnaryCall(channel, LOOKUP,
                CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS), StringValue.of(sku)).getValue();
    }

    private static final class AuditSession {
        private final String id;
        private final InetSocketAddress peer;
        private final AtomicInteger requests = new AtomicInteger();

        private AuditSession(String id, InetSocketAddress peer) {
            this.id = id;
            this.peer = peer;
        }
    }

    private record CompletedAudit(String sessionId, int requests) {
    }

    private static final class ConnectionAuditor extends ServerTransportFilter implements ServerInterceptor {
        private final AtomicInteger sequence = new AtomicInteger();
        private final Map<String, AuditSession> active = new ConcurrentHashMap<>();
        private final BlockingQueue<CompletedAudit> completed = new LinkedBlockingQueue<>();

        @Override
        public Attributes transportReady(Attributes attributes) {
            InetSocketAddress peer = (InetSocketAddress) attributes.get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
            AuditSession session = new AuditSession("connection-" + sequence.incrementAndGet(), peer);
            active.put(session.id, session);
            return attributes.toBuilder().set(SESSION, session).build();
        }

        @Override
        public void transportTerminated(Attributes attributes) {
            AuditSession session = attributes.get(SESSION);
            if (session != null) {
                active.remove(session.id);
                completed.add(new CompletedAudit(session.id, session.requests.get()));
            }
        }

        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                Metadata headers, ServerCallHandler<ReqT, RespT> next) {
            AuditSession session = Objects.requireNonNull(call.getAttributes().get(SESSION));
            session.requests.incrementAndGet();
            return next.startCall(new SimpleForwardingServerCall<>(call) {
                @Override
                public void sendHeaders(Metadata responseHeaders) {
                    responseHeaders.put(SESSION_HEADER, session.id);
                    super.sendHeaders(responseHeaders);
                }
            }, headers);
        }
    }

    private static final class CatalogService {
        private final Map<String, String> products;

        private CatalogService(Map<String, String> products) {
            this.products = Map.copyOf(products);
        }

        private ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder(SERVICE_NAME)
                    .addMethod(LOOKUP, ServerCalls.asyncUnaryCall(this::lookup))
                    .build();
        }

        private void lookup(StringValue request, StreamObserver<StringValue> response) {
            response.onNext(StringValue.of(products.getOrDefault(request.getValue(), "Unknown product")));
            response.onCompleted();
        }
    }
}
