/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_resolver;

import java.io.StringReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.CompositeNameResolver;
import io.netty.resolver.DefaultNameResolver;
import io.netty.resolver.HostsFileEntries;
import io.netty.resolver.HostsFileEntriesProvider;
import io.netty.resolver.HostsFileParser;
import io.netty.resolver.InetSocketAddressResolver;
import io.netty.resolver.RoundRobinInetAddressResolver;
import io.netty.resolver.SimpleNameResolver;
import io.netty.util.concurrent.DefaultEventExecutor;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Netty_resolverTest {

    private static final EventExecutor EXECUTOR = ImmediateEventExecutor.INSTANCE;

    @Test
    void hostsFileParsersNormalizeAliasesAndPreserveProviderAddressLists() throws Exception {
        String hostsFile = """
                # loopback aliases
                127.0.0.1 LOCALHOST loopback
                10.0.0.10 Example.Test alias
                10.0.0.11 example.test
                ::1 LOCALHOST ip6-localhost
                2001:db8::1 Example.Test
                not-an-ip ignored.example
                """;

        HostsFileEntriesProvider provider = HostsFileEntriesProvider.parser().parse(new StringReader(hostsFile));

        assertThat(provider.ipv4Entries()).containsKeys("localhost", "loopback", "example.test", "alias");
        assertThat(provider.ipv6Entries()).containsKeys("localhost", "ip6-localhost", "example.test");
        assertThat(provider.ipv4Entries()).doesNotContainKey("LOCALHOST");
        assertThat(provider.ipv4Entries().get("example.test"))
                .containsExactly(ip("10.0.0.10"), ip("10.0.0.11"));
        assertThat(provider.ipv6Entries().get("example.test"))
                .containsExactly(ip("2001:db8::1"));

        HostsFileEntries entries = HostsFileParser.parse(new StringReader(hostsFile));

        assertThat(entries.inet4Entries().get("localhost")).isEqualTo(ip("127.0.0.1"));
        assertThat(entries.inet6Entries().get("localhost")).isEqualTo(ip("::1"));
        assertThat(entries.inet4Entries().get("example.test")).isEqualTo(ip("10.0.0.10"));
        assertThat(entries.inet6Entries().get("example.test")).isEqualTo(ip("2001:db8::1"));
        assertThat(entries.inet4Entries()).doesNotContainKey("ignored.example");
    }

    @Test
    void defaultNameResolverResolvesIpv4AndIpv6Literals() throws Exception {
        DefaultNameResolver resolver = new DefaultNameResolver(EXECUTOR);

        try {
            InetAddress resolvedIpv4 = resolver.resolve("198.51.100.70").syncUninterruptibly().getNow();
            List<InetAddress> resolvedAllIpv4 = resolver.resolveAll("198.51.100.70").syncUninterruptibly().getNow();
            InetAddress resolvedIpv6 = resolver.resolve("2001:db8::70").syncUninterruptibly().getNow();
            List<InetAddress> resolvedAllIpv6 = resolver.resolveAll("2001:db8::70").syncUninterruptibly().getNow();

            assertThat(resolvedIpv4).isEqualTo(ip("198.51.100.70"));
            assertThat(resolvedAllIpv4).containsExactly(ip("198.51.100.70"));
            assertThat(resolvedIpv6).isEqualTo(ip("2001:db8::70"));
            assertThat(resolvedAllIpv6).containsExactly(ip("2001:db8::70"));
        } finally {
            resolver.close();
        }
    }

    @Test
    void compositeNameResolverFallsBackToLaterResolvers() throws Exception {
        RecordingNameResolver firstResolver = new RecordingNameResolver(EXECUTOR, Map.of());
        RecordingNameResolver secondResolver = new RecordingNameResolver(
                EXECUTOR,
                Map.of("example.test", List.of(ip("203.0.113.10"), ip("203.0.113.11")))
        );
        CompositeNameResolver<InetAddress> resolver = new CompositeNameResolver<>(EXECUTOR, firstResolver, secondResolver);

        try {
            InetAddress resolved = resolver.resolve("example.test").syncUninterruptibly().getNow();
            List<InetAddress> resolvedAll = resolver.resolveAll("example.test").syncUninterruptibly().getNow();

            assertThat(resolved).isEqualTo(ip("203.0.113.10"));
            assertThat(resolvedAll).containsExactly(ip("203.0.113.10"), ip("203.0.113.11"));
            assertThat(firstResolver.resolveHosts).containsExactly("example.test");
            assertThat(firstResolver.resolveAllHosts).containsExactly("example.test");
            assertThat(secondResolver.resolveHosts).containsExactly("example.test");
            assertThat(secondResolver.resolveAllHosts).containsExactly("example.test");
        } finally {
            resolver.close();
        }
    }

    @Test
    void inetSocketAddressResolverResolvesSingleAndAllAddressesWithOriginalPort() throws Exception {
        RecordingNameResolver nameResolver = new RecordingNameResolver(
                EXECUTOR,
                Map.of("example.test", List.of(ip("198.51.100.20"), ip("198.51.100.21")))
        );
        InetSocketAddressResolver resolver = new InetSocketAddressResolver(EXECUTOR, nameResolver);
        InetSocketAddress unresolved = InetSocketAddress.createUnresolved("example.test", 8443);

        try {
            assertThat(resolver.isSupported(unresolved)).isTrue();
            assertThat(resolver.isResolved(unresolved)).isFalse();

            InetSocketAddress resolved = resolver.resolve(unresolved).syncUninterruptibly().getNow();
            List<InetSocketAddress> resolvedAll = resolver.resolveAll(unresolved).syncUninterruptibly().getNow();

            assertThat(resolved.isUnresolved()).isFalse();
            assertThat(resolved.getAddress()).isEqualTo(ip("198.51.100.20"));
            assertThat(resolved.getPort()).isEqualTo(8443);
            assertThat(resolvedAll)
                    .extracting(InetSocketAddress::getAddress)
                    .containsExactly(ip("198.51.100.20"), ip("198.51.100.21"));
            assertThat(resolvedAll)
                    .extracting(InetSocketAddress::getPort)
                    .containsOnly(8443);
            assertThat(nameResolver.resolveHosts).containsExactly("example.test");
            assertThat(nameResolver.resolveAllHosts).containsExactly("example.test");
        } finally {
            resolver.close();
        }

        assertThat(nameResolver.closed).isTrue();
    }

    @Test
    void addressResolverGroupCachesResolversPerExecutorAndClosesCreatedResolvers() throws Exception {
        RecordingAddressResolverGroup group = new RecordingAddressResolverGroup(
                Map.of("example.test", List.of(ip("192.0.2.44")))
        );
        DefaultEventExecutor otherExecutor = new DefaultEventExecutor();

        try {
            AddressResolver<InetSocketAddress> firstResolver = group.getResolver(EXECUTOR);
            AddressResolver<InetSocketAddress> cachedResolver = group.getResolver(EXECUTOR);
            AddressResolver<InetSocketAddress> otherResolver = group.getResolver(otherExecutor);

            assertThat(firstResolver).isSameAs(cachedResolver);
            assertThat(otherResolver).isNotSameAs(firstResolver);
            assertThat(group.createdNameResolvers).hasSize(2);

            InetSocketAddress resolved = firstResolver
                    .resolve(InetSocketAddress.createUnresolved("example.test", 8080))
                    .syncUninterruptibly()
                    .getNow();

            assertThat(resolved.getAddress()).isEqualTo(ip("192.0.2.44"));
            assertThat(resolved.getPort()).isEqualTo(8080);

            group.close();

            assertThat(group.createdNameResolvers)
                    .allSatisfy(nameResolver -> assertThat(nameResolver.closed).isTrue());
        } finally {
            otherExecutor.shutdownGracefully().syncUninterruptibly();
        }
    }

    @Test
    void roundRobinInetAddressResolverUsesResolveAllResultsForSingleAndBulkLookups() throws Exception {
        List<InetAddress> addresses = List.of(ip("203.0.113.30"), ip("203.0.113.31"), ip("203.0.113.32"));
        RecordingNameResolver nameResolver = new RecordingNameResolver(
                EXECUTOR,
                Map.of("roundrobin.test", addresses)
        );
        RoundRobinInetAddressResolver resolver = new RoundRobinInetAddressResolver(EXECUTOR, nameResolver);

        try {
            InetAddress resolved = resolver.resolve("roundrobin.test").syncUninterruptibly().getNow();
            List<InetAddress> resolvedAll = resolver.resolveAll("roundrobin.test").syncUninterruptibly().getNow();

            assertThat(resolved).isIn(addresses);
            assertThat(resolvedAll).containsExactlyInAnyOrderElementsOf(addresses);
            assertThat(nameResolver.resolveHosts).isEmpty();
            assertThat(nameResolver.resolveAllHosts).containsExactly("roundrobin.test", "roundrobin.test");
        } finally {
            resolver.close();
        }

        assertThat(nameResolver.closed).isTrue();
    }

    private static InetAddress ip(String literal) throws UnknownHostException {
        return InetAddress.getByName(literal);
    }

    private static final class RecordingNameResolver extends SimpleNameResolver<InetAddress> {
        private final Map<String, List<InetAddress>> addressesByHost;
        private final List<String> resolveHosts = new ArrayList<>();
        private final List<String> resolveAllHosts = new ArrayList<>();
        private boolean closed;

        private RecordingNameResolver(EventExecutor executor, Map<String, List<InetAddress>> addressesByHost) {
            super(executor);
            this.addressesByHost = addressesByHost;
        }

        @Override
        protected void doResolve(String inetHost, Promise<InetAddress> promise) throws Exception {
            resolveHosts.add(inetHost);
            List<InetAddress> addresses = addressesByHost.get(normalize(inetHost));
            if (addresses == null || addresses.isEmpty()) {
                promise.setFailure(new UnknownHostException(inetHost));
                return;
            }
            promise.setSuccess(addresses.get(0));
        }

        @Override
        protected void doResolveAll(String inetHost, Promise<List<InetAddress>> promise) throws Exception {
            resolveAllHosts.add(inetHost);
            List<InetAddress> addresses = addressesByHost.get(normalize(inetHost));
            if (addresses == null || addresses.isEmpty()) {
                promise.setFailure(new UnknownHostException(inetHost));
                return;
            }
            promise.setSuccess(List.copyOf(addresses));
        }

        @Override
        public void close() {
            closed = true;
        }

        private static String normalize(String host) {
            return host.toLowerCase(Locale.ENGLISH);
        }
    }

    private static final class RecordingAddressResolverGroup extends AddressResolverGroup<InetSocketAddress> {
        private final Map<String, List<InetAddress>> addressesByHost;
        private final List<RecordingNameResolver> createdNameResolvers = new ArrayList<>();

        private RecordingAddressResolverGroup(Map<String, List<InetAddress>> addressesByHost) {
            this.addressesByHost = addressesByHost;
        }

        @Override
        protected AddressResolver<InetSocketAddress> newResolver(EventExecutor executor) {
            RecordingNameResolver nameResolver = new RecordingNameResolver(executor, addressesByHost);
            createdNameResolvers.add(nameResolver);
            return new InetSocketAddressResolver(executor, nameResolver);
        }
    }
}
