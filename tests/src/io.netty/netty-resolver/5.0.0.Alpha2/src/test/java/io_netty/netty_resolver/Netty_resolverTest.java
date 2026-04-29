/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_resolver;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.netty.resolver.DefaultNameResolver;
import io.netty.resolver.NameResolver;
import io.netty.resolver.NameResolverGroup;
import io.netty.resolver.NoopNameResolver;
import io.netty.resolver.NoopNameResolverGroup;
import io.netty.resolver.SimpleNameResolver;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Netty_resolverTest {

    private static final EventExecutor EXECUTOR = ImmediateEventExecutor.INSTANCE;

    @Test
    void defaultNameResolverResolvesIpv4AndIpv6SocketAddresses() throws Exception {
        DefaultNameResolver resolver = new DefaultNameResolver(EXECUTOR);

        try {
            InetSocketAddress unresolvedIpv4 = InetSocketAddress.createUnresolved("198.51.100.70", 8080);
            InetSocketAddress resolvedIpv4 = resolver.resolve(unresolvedIpv4).syncUninterruptibly().getNow();
            InetSocketAddress resolvedByHostAndPort = resolver.resolve("198.51.100.71", 8443)
                    .syncUninterruptibly()
                    .getNow();
            InetSocketAddress unresolvedIpv6 = InetSocketAddress.createUnresolved("2001:db8::70", 9090);
            InetSocketAddress resolvedIpv6 = resolver.resolve(unresolvedIpv6).syncUninterruptibly().getNow();

            assertThat(resolvedIpv4.isUnresolved()).isFalse();
            assertThat(resolvedIpv4.getAddress()).isEqualTo(ip("198.51.100.70"));
            assertThat(resolvedIpv4.getPort()).isEqualTo(8080);
            assertThat(resolvedByHostAndPort.getAddress()).isEqualTo(ip("198.51.100.71"));
            assertThat(resolvedByHostAndPort.getPort()).isEqualTo(8443);
            assertThat(resolvedIpv6.isUnresolved()).isFalse();
            assertThat(resolvedIpv6.getAddress()).isEqualTo(ip("2001:db8::70"));
            assertThat(resolvedIpv6.getPort()).isEqualTo(9090);
        } finally {
            resolver.close();
        }
    }

    @Test
    void simpleNameResolverReportsSupportAndReturnsAlreadyResolvedAddresses() throws Exception {
        RecordingNameResolver resolver = new RecordingNameResolver(
                EXECUTOR,
                Map.of("example.test", List.of(ip("203.0.113.10")))
        );
        InetSocketAddress resolvedAddress = new InetSocketAddress(ip("203.0.113.20"), 443);
        SocketAddress unsupportedAddress = new SocketAddress() { };

        try {
            InetSocketAddress result = resolver.resolve(resolvedAddress).syncUninterruptibly().getNow();

            assertThat(resolver.isSupported(resolvedAddress)).isTrue();
            assertThat(resolver.isSupported(unsupportedAddress)).isFalse();
            assertThat(resolver.isResolved(resolvedAddress)).isTrue();
            assertThat(result).isSameAs(resolvedAddress);
            assertThat(resolver.resolveHosts).isEmpty();
            assertThatThrownBy(() -> resolver.isResolved(unsupportedAddress))
                    .isInstanceOf(UnsupportedAddressTypeException.class);
        } finally {
            resolver.close();
        }

        assertThat(resolver.closed).isTrue();
    }

    @Test
    void simpleNameResolverResolvesUnresolvedAddressesAndPropagatesFailures() throws Exception {
        RecordingNameResolver resolver = new RecordingNameResolver(
                EXECUTOR,
                Map.of("example.test", List.of(ip("203.0.113.30")))
        );

        try {
            InetSocketAddress unresolved = InetSocketAddress.createUnresolved("Example.Test", 8443);
            InetSocketAddress resolved = resolver.resolve(unresolved).syncUninterruptibly().getNow();
            Throwable failure = resolver.resolve("missing.test", 9443).awaitUninterruptibly().cause();

            assertThat(resolved.isUnresolved()).isFalse();
            assertThat(resolved.getAddress()).isEqualTo(ip("203.0.113.30"));
            assertThat(resolved.getPort()).isEqualTo(8443);
            assertThat(resolver.resolveHosts).containsExactly("Example.Test", "missing.test");
            assertThat(failure).isInstanceOf(UnknownHostException.class)
                    .hasMessage("missing.test");
        } finally {
            resolver.close();
        }
    }

    @Test
    void nameResolverGroupCachesResolverAndClosesCreatedResolver() throws Exception {
        RecordingNameResolverGroup group = new RecordingNameResolverGroup(
                Map.of("example.test", List.of(ip("192.0.2.44")))
        );
        try {
            NameResolver<InetSocketAddress> firstResolver = group.getResolver(EXECUTOR);
            NameResolver<InetSocketAddress> cachedResolver = group.getResolver(EXECUTOR);

            assertThat(firstResolver).isSameAs(cachedResolver);
            assertThat(group.createdNameResolvers).hasSize(1);

            InetSocketAddress resolved = firstResolver
                    .resolve("example.test", 8080)
                    .syncUninterruptibly()
                    .getNow();

            assertThat(resolved.getAddress()).isEqualTo(ip("192.0.2.44"));
            assertThat(resolved.getPort()).isEqualTo(8080);

            group.close();

            assertThat(group.createdNameResolvers)
                    .allSatisfy(nameResolver -> assertThat(nameResolver.closed).isTrue());
        } finally {
            group.close();
        }
    }

    @Test
    void noopNameResolverTreatsSocketAddressesAsAlreadyResolved() {
        NoopNameResolver resolver = new NoopNameResolver(EXECUTOR);
        InetSocketAddress unresolved = InetSocketAddress.createUnresolved("unresolved.example", 8080);

        try {
            SocketAddress resolved = resolver.resolve(unresolved).syncUninterruptibly().getNow();

            assertThat(resolver.isSupported(unresolved)).isTrue();
            assertThat(resolver.isResolved(unresolved)).isTrue();
            assertThat(resolved).isSameAs(unresolved);
        } finally {
            resolver.close();
        }
    }

    @Test
    void noopNameResolverGroupReusesResolverForExecutor() {
        try {
            NameResolver<SocketAddress> resolver = NoopNameResolverGroup.INSTANCE.getResolver(EXECUTOR);
            NameResolver<SocketAddress> cachedResolver = NoopNameResolverGroup.INSTANCE.getResolver(EXECUTOR);
            InetSocketAddress unresolved = InetSocketAddress.createUnresolved("pipeline-resolved.example", 443);
            SocketAddress resolved = resolver.resolve(unresolved).syncUninterruptibly().getNow();

            assertThat(resolver).isSameAs(cachedResolver);
            assertThat(resolved).isSameAs(unresolved);
        } finally {
            NoopNameResolverGroup.INSTANCE.close();
        }
    }

    private static InetAddress ip(String literal) throws UnknownHostException {
        return InetAddress.getByName(literal);
    }

    private static final class RecordingNameResolver extends SimpleNameResolver<InetSocketAddress> {
        private final Map<String, List<InetAddress>> addressesByHost;
        private final List<String> resolveHosts = new ArrayList<>();
        private boolean closed;

        private RecordingNameResolver(EventExecutor executor, Map<String, List<InetAddress>> addressesByHost) {
            super(executor, InetSocketAddress.class);
            this.addressesByHost = addressesByHost;
        }

        @Override
        protected boolean doIsResolved(InetSocketAddress address) {
            return !address.isUnresolved();
        }

        @Override
        protected void doResolve(
                InetSocketAddress unresolvedAddress,
                Promise<InetSocketAddress> promise
        ) throws Exception {
            String inetHost = unresolvedAddress.getHostString();
            resolveHosts.add(inetHost);
            List<InetAddress> addresses = addressesByHost.get(normalize(inetHost));
            if (addresses == null || addresses.isEmpty()) {
                promise.setFailure(new UnknownHostException(inetHost));
                return;
            }
            promise.setSuccess(new InetSocketAddress(addresses.get(0), unresolvedAddress.getPort()));
        }

        @Override
        public void close() {
            closed = true;
        }

        private static String normalize(String host) {
            return host.toLowerCase(Locale.ENGLISH);
        }
    }

    private static final class RecordingNameResolverGroup extends NameResolverGroup<InetSocketAddress> {
        private final Map<String, List<InetAddress>> addressesByHost;
        private final List<RecordingNameResolver> createdNameResolvers = new ArrayList<>();

        private RecordingNameResolverGroup(Map<String, List<InetAddress>> addressesByHost) {
            this.addressesByHost = addressesByHost;
        }

        @Override
        protected NameResolver<InetSocketAddress> newResolver(EventExecutor executor) {
            RecordingNameResolver nameResolver = new RecordingNameResolver(executor, addressesByHost);
            createdNameResolvers.add(nameResolver);
            return nameResolver;
        }
    }
}
