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
import io.grpc.HttpConnectProxiedSocketAddress;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ProxyDetector;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tunnels grpc-netty client connections through an authenticating HTTP CONNECT proxy,
 * selected per target by a `ProxyDetector` on `NettyChannelBuilder` that returns an
 * `HttpConnectProxiedSocketAddress` with proxy credentials.
 *
 * A billing client inside a corporate network reaches the invoice service by an internal
 * host name that only the egress proxy can resolve. The proxy checks the `Proxy-Authorization`
 * credentials of each `CONNECT` request, resolves the internal name, and relays the HTTP/2
 * bytes; the server observes the proxy's outbound socket as the caller, and a client with
 * the wrong password is refused before any call reaches the service.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HttpConnectProxyBillingTest {

    private static final String SERVICE = "billing.Invoices";

    private static final String INTERNAL_HOST = "invoices.billing.corp";

    private static final String PROXY_USER = "billing-client";

    private static final String PROXY_PASSWORD = "s3cret-egress";

    private static final MethodDescriptor<StringValue, Int64Value> TOTAL =
            MethodDescriptor.<StringValue, Int64Value>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE, "Total"))
                    .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .setResponseMarshaller(ProtoUtils.marshaller(Int64Value.getDefaultInstance()))
                    .build();

    private static final MethodDescriptor<StringValue, StringValue> LINES =
            MethodDescriptor.<StringValue, StringValue>newBuilder()
                    .setType(MethodDescriptor.MethodType.SERVER_STREAMING)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE, "Lines"))
                    .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .setResponseMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .build();

    private static final Map<String, List<String>> INVOICES = Map.of(
            "INV-1001", List.of("hosting:12000", "support:3500", "licences:4250"),
            "INV-1002", List.of("consulting:80000"));

    private final List<InetSocketAddress> callerAddresses = new CopyOnWriteArrayList<>();

    private Server server;

    private EgressProxy proxy;

    private ManagedChannel channel;

    @BeforeAll
    void startServerProxyAndChannel() throws Exception {
        server = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
                .addService(ServerInterceptors.intercept(bindInvoiceService(), new CallerAddressInterceptor()))
                .build()
                .start();
        proxy = new EgressProxy(Map.of(INTERNAL_HOST, InetAddress.getLoopbackAddress()),
                PROXY_USER, PROXY_PASSWORD);
        channel = proxiedChannel(PROXY_USER, PROXY_PASSWORD);
    }

    @AfterAll
    void stopServerProxyAndChannel() throws Exception {
        channel.shutdownNow();
        server.shutdown();
        assertThat(channel.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(server.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        proxy.close();
    }

    @Test
    void tunnelsCallsToAnInternalHostThroughTheAuthenticatedProxy() {
        Int64Value total = ClientCalls.blockingUnaryCall(channel, TOTAL,
                CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS), text("INV-1001"));
        Iterator<StringValue> lines = ClientCalls.blockingServerStreamingCall(channel, LINES,
                CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS), text("INV-1001"));
        List<String> received = new ArrayList<>();
        lines.forEachRemaining(line -> received.add(line.getValue()));

        assertThat(total.getValue()).isEqualTo(19_750L);
        assertThat(received).containsExactly("hosting:12000", "support:3500", "licences:4250");
        assertThat(channel.authority()).isEqualTo(INTERNAL_HOST + ":" + server.getPort());

        List<ConnectRequest> accepted = proxy.requests().stream().filter(ConnectRequest::accepted).toList();
        assertThat(accepted).hasSize(1);
        ConnectRequest tunnel = accepted.get(0);
        assertThat(tunnel.target()).isEqualTo(INTERNAL_HOST + ":" + server.getPort());
        assertThat(tunnel.credentials()).isEqualTo(PROXY_USER + ":" + PROXY_PASSWORD);
        assertThat(callerAddresses).isNotEmpty()
                .allSatisfy(caller -> assertThat(caller.getPort()).isEqualTo(tunnel.upstreamLocalPort()));
    }

    @Test
    void reportsUnknownInvoicesThroughTheTunnel() {
        assertThatThrownBy(() -> ClientCalls.blockingUnaryCall(channel, TOTAL,
                CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS), text("INV-9999")))
                .isInstanceOfSatisfying(StatusRuntimeException.class, e -> {
                    assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
                    assertThat(e.getStatus().getDescription()).isEqualTo("no invoice INV-9999");
                });
    }

    @Test
    void refusesClientsWithWrongProxyCredentials() throws Exception {
        int callsBefore = callerAddresses.size();
        ManagedChannel intruder = proxiedChannel(PROXY_USER, "guessed-password");
        try {
            assertThatThrownBy(() -> ClientCalls.blockingUnaryCall(intruder, TOTAL,
                    CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS), text("INV-1002")))
                    .isInstanceOfSatisfying(StatusRuntimeException.class, e ->
                            assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE));
        } finally {
            intruder.shutdownNow();
            assertThat(intruder.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(proxy.requests())
                .filteredOn(request -> !request.accepted())
                .isNotEmpty()
                .allSatisfy(request -> {
                    assertThat(request.target()).isEqualTo(INTERNAL_HOST + ":" + server.getPort());
                    assertThat(request.credentials()).isEqualTo(PROXY_USER + ":guessed-password");
                });
        assertThat(callerAddresses).hasSize(callsBefore);
    }

    private ManagedChannel proxiedChannel(String user, String password) {
        InetSocketAddress proxyAddress = proxy.address();
        ProxyDetector viaEgressProxy = (SocketAddress target) -> HttpConnectProxiedSocketAddress.newBuilder()
                .setTargetAddress((InetSocketAddress) target)
                .setProxyAddress(proxyAddress)
                .setUsername(user)
                .setPassword(password)
                .build();
        return NettyChannelBuilder.forTarget("dns:///" + INTERNAL_HOST + ":" + server.getPort())
                .proxyDetector(viaEgressProxy)
                .usePlaintext()
                .build();
    }

    private static ServerServiceDefinition bindInvoiceService() {
        return ServerServiceDefinition.builder(SERVICE)
                .addMethod(TOTAL, ServerCalls.asyncUnaryCall(HttpConnectProxyBillingTest::total))
                .addMethod(LINES, ServerCalls.asyncServerStreamingCall(HttpConnectProxyBillingTest::lines))
                .build();
    }

    private static void total(StringValue invoice, StreamObserver<Int64Value> response) {
        List<String> lines = INVOICES.get(invoice.getValue());
        if (lines == null) {
            response.onError(Status.NOT_FOUND.withDescription("no invoice " + invoice.getValue())
                    .asRuntimeException());
            return;
        }
        long sum = lines.stream().mapToLong(line -> Long.parseLong(line.substring(line.indexOf(':') + 1))).sum();
        response.onNext(Int64Value.of(sum));
        response.onCompleted();
    }

    private static void lines(StringValue invoice, StreamObserver<StringValue> response) {
        for (String line : INVOICES.get(invoice.getValue())) {
            response.onNext(text(line));
        }
        response.onCompleted();
    }

    private static StringValue text(String value) {
        return StringValue.newBuilder().setValue(value).build();
    }

    /** Records the remote address the server transport reports for every call. */
    private final class CallerAddressInterceptor implements ServerInterceptor {

        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
            callerAddresses.add((InetSocketAddress) call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR));
            return next.startCall(call, headers);
        }
    }

    /** One `CONNECT` request as the proxy saw it, with the local port of its upstream socket if accepted. */
    private record ConnectRequest(String target, String credentials, boolean accepted, int upstreamLocalPort) {
    }

    /** A minimal authenticating HTTP CONNECT proxy that resolves internal names and relays bytes. */
    private static final class EgressProxy implements AutoCloseable {

        private final Map<String, InetAddress> internalHosts;

        private final String expectedCredentials;

        private final ServerSocket listener;

        private final List<ConnectRequest> requests = new CopyOnWriteArrayList<>();

        private final List<Socket> sockets = new CopyOnWriteArrayList<>();

        EgressProxy(Map<String, InetAddress> internalHosts, String user, String password) throws IOException {
            this.internalHosts = internalHosts;
            this.expectedCredentials = user + ":" + password;
            this.listener = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            Thread acceptor = new Thread(this::acceptConnections, "egress-proxy-acceptor");
            acceptor.setDaemon(true);
            acceptor.start();
        }

        InetSocketAddress address() {
            return new InetSocketAddress(listener.getInetAddress(), listener.getLocalPort());
        }

        List<ConnectRequest> requests() {
            return requests;
        }

        private void acceptConnections() {
            while (!listener.isClosed()) {
                try {
                    Socket client = listener.accept();
                    sockets.add(client);
                    Thread handler = new Thread(() -> handle(client), "egress-proxy-tunnel");
                    handler.setDaemon(true);
                    handler.start();
                } catch (IOException closed) {
                    return;
                }
            }
        }

        private void handle(Socket client) {
            try {
                InputStream fromClient = client.getInputStream();
                OutputStream toClient = client.getOutputStream();
                String[] head = readHead(fromClient).split("\r\n");
                String[] requestLine = head[0].split(" ");
                if (!requestLine[0].equals("CONNECT")) {
                    respond(toClient, "405 Method Not Allowed");
                    client.close();
                    return;
                }
                String target = requestLine[1];
                String credentials = basicCredentials(head);
                if (!expectedCredentials.equals(credentials)) {
                    requests.add(new ConnectRequest(target, credentials, false, -1));
                    respond(toClient, "407 Proxy Authentication Required\r\n"
                            + "Proxy-Authenticate: Basic realm=\"corp-egress\"");
                    client.close();
                    return;
                }
                int colon = target.lastIndexOf(':');
                InetAddress host = internalHosts.get(target.substring(0, colon));
                Socket upstream = new Socket(host, Integer.parseInt(target.substring(colon + 1)));
                sockets.add(upstream);
                requests.add(new ConnectRequest(target, credentials, true, upstream.getLocalPort()));
                respond(toClient, "200 Connection Established");
                Thread downstream = new Thread(() -> relay(upstream, client), "egress-proxy-downstream");
                downstream.setDaemon(true);
                downstream.start();
                relay(client, upstream);
            } catch (IOException ignored) {
                closeQuietly(client);
            }
        }

        private static String readHead(InputStream in) throws IOException {
            ByteArrayOutputStream head = new ByteArrayOutputStream();
            int matched = 0;
            byte[] terminator = {'\r', '\n', '\r', '\n'};
            while (matched < terminator.length) {
                int b = in.read();
                if (b < 0) {
                    throw new IOException("client closed before the request head ended");
                }
                head.write(b);
                matched = b == terminator[matched] ? matched + 1 : (b == '\r' ? 1 : 0);
            }
            return head.toString(StandardCharsets.US_ASCII);
        }

        private static String basicCredentials(String[] head) {
            for (int i = 1; i < head.length; i++) {
                int colon = head[i].indexOf(':');
                if (colon > 0 && head[i].substring(0, colon).trim().toLowerCase(Locale.ROOT)
                        .equals("proxy-authorization")) {
                    String value = head[i].substring(colon + 1).trim();
                    if (value.startsWith("Basic ")) {
                        byte[] decoded = Base64.getDecoder().decode(value.substring("Basic ".length()));
                        return new String(decoded, StandardCharsets.UTF_8);
                    }
                }
            }
            return null;
        }

        private static void respond(OutputStream out, String statusAndHeaders) throws IOException {
            out.write(("HTTP/1.1 " + statusAndHeaders + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
        }

        private static void relay(Socket from, Socket to) {
            byte[] buffer = new byte[8192];
            try {
                InputStream in = from.getInputStream();
                OutputStream out = to.getOutputStream();
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    out.write(buffer, 0, read);
                    out.flush();
                }
            } catch (IOException ignored) {
                // One side closed the tunnel; the finally block tears down the other side.
            } finally {
                closeQuietly(from);
                closeQuietly(to);
            }
        }

        private static void closeQuietly(Socket socket) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Already closed.
            }
        }

        @Override
        public void close() throws IOException {
            listener.close();
            sockets.forEach(EgressProxy::closeQuietly);
        }
    }
}
