/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.grpc.CallOptions;
import io.grpc.EquivalentAddressGroup;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.NameResolver;
import io.grpc.NameResolverProvider;
import io.grpc.NameResolverRegistry;
import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.inprocess.InProcessSocketAddress;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;

final class RpcTestSupport implements AutoCloseable {
    static final String SERVICE = "reachability.UtilityService";
    static final MethodDescriptor<String, String> METHOD = MethodDescriptor.<String, String>newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE, "Reply"))
            .setRequestMarshaller(new StringMarshaller())
            .setResponseMarshaller(new StringMarshaller())
            .build();

    private final List<Server> servers = new ArrayList<>();
    private final List<ManagedChannel> channels = new ArrayList<>();
    private final List<NameResolverProvider> resolvers = new ArrayList<>();

    static ServerServiceDefinition service(ServerCalls.UnaryMethod<String, String> handler) {
        return ServerServiceDefinition.builder(SERVICE)
                .addMethod(METHOD, ServerCalls.asyncUnaryCall(handler))
                .build();
    }

    static ServerServiceDefinition echoService(String prefix) {
        return service((request, observer) -> {
            observer.onNext(prefix + request);
            observer.onCompleted();
        });
    }

    Server startServer(InProcessServerBuilder builder) throws IOException {
        Server server = builder.directExecutor().build().start();
        servers.add(server);
        return server;
    }

    ManagedChannel openChannel(InProcessChannelBuilder builder) {
        ManagedChannel channel = builder.directExecutor().build();
        channels.add(channel);
        return channel;
    }

    static String call(ManagedChannel channel, String request) {
        return ClientCalls.blockingUnaryCall(channel, METHOD,
                CallOptions.DEFAULT.withDeadlineAfter(30, TimeUnit.SECONDS), request);
    }

    void registerResolver(NameResolverProvider provider) {
        NameResolverRegistry.getDefaultRegistry().register(provider);
        resolvers.add(provider);
    }

    static NameResolverProvider resolverFor(List<String> serverNames) {
        List<EquivalentAddressGroup> addresses = serverNames.stream()
                .map(name -> new EquivalentAddressGroup(new InProcessSocketAddress(name)))
                .toList();
        return new NameResolverProvider() {
            @Override
            protected boolean isAvailable() {
                return true;
            }

            @Override
            protected int priority() {
                return 5;
            }

            @Override
            public Collection<Class<? extends SocketAddress>> getProducedSocketAddressTypes() {
                return List.of(InProcessSocketAddress.class);
            }

            @Override
            public NameResolver newNameResolver(URI targetUri, NameResolver.Args args) {
                if (!getDefaultScheme().equals(targetUri.getScheme())) {
                    return null;
                }
                return new NameResolver() {
                    @Override
                    public String getServiceAuthority() {
                        return "utility-test";
                    }

                    @Override
                    public void start(Listener2 listener) {
                        listener.onResult(ResolutionResult.newBuilder().setAddresses(addresses).build());
                    }

                    @Override
                    public void shutdown() {
                    }
                };
            }

            @Override
            public String getDefaultScheme() {
                return "utility-test";
            }
        };
    }

    @Override
    public void close() throws InterruptedException {
        channels.forEach(ManagedChannel::shutdownNow);
        servers.forEach(Server::shutdownNow);
        resolvers.forEach(NameResolverRegistry.getDefaultRegistry()::deregister);
        for (ManagedChannel channel : channels) {
            assertThat(channel.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
        for (Server server : servers) {
            assertThat(server.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static final class StringMarshaller implements MethodDescriptor.Marshaller<String> {
        @Override
        public InputStream stream(String value) {
            return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String parse(InputStream stream) {
            try {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw Status.INTERNAL.withDescription("Cannot read RPC message")
                        .withCause(exception).asRuntimeException();
            }
        }
    }
}
