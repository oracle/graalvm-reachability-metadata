/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_common;

import io.netty.util.NetUtil;
import org.junit.jupiter.api.Test;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the IP address utility subsystem of netty-common: `NetUtil` validation of IPv4 and
 * IPv6 literals, their parsing into raw bytes and `InetAddress` objects without DNS lookups,
 * RFC 5952 canonical formatting, IPv4-to-int conversion, socket-address formatting and the
 * loopback constants resolved when the class initializes.
 *
 * An edge gateway reads client addresses from a forwarded-for header. It rejects entries that are
 * not IP literals, canonicalizes the rest so that every spelling of one IPv6 address lands on the
 * same rate-limit key, checks IPv4 clients against CIDR allow-list ranges, and writes
 * `host:port` access-log entries with bracketed IPv6 hosts.
 */
class ClientAddressAllowListNetUtilTest {

    @Test
    void forwardedForEntriesAreValidatedAndCanonicalized() {
        ClientAddressNormalizer normalizer = new ClientAddressNormalizer();

        List<String> keys = normalizer.rateLimitKeys(
                "203.0.113.7, 2001:DB8:0:0:0:0:0:1, 2001:db8::1, api.example.com, 2001:0db8:0000:0000:0000:ff00:0042:8329,"
                        + " 999.1.1.1, ::ffff:198.51.100.4, 10.0.0.1:8080");

        assertThat(keys).containsExactly(
                "203.0.113.7",
                "2001:db8::1",
                "2001:db8::1",
                "2001:db8::ff00:42:8329",
                "::ffff:198.51.100.4");
        assertThat(normalizer.rejected()).containsExactly("api.example.com", "999.1.1.1", "10.0.0.1:8080");
    }

    @Test
    void literalsParseToAddressesWithoutNameResolution() {
        assertThat(NetUtil.isValidIpV4Address("192.168.1.20")).isTrue();
        assertThat(NetUtil.isValidIpV4Address("192.168.1")).isFalse();
        assertThat(NetUtil.isValidIpV6Address("fe80::1%eth0")).isTrue();
        assertThat(NetUtil.isValidIpV6Address("[2001:db8::1]")).isTrue();
        assertThat(NetUtil.isValidIpV6Address("2001:db8:::1")).isFalse();

        assertThat(NetUtil.createByteArrayFromIpAddressString("192.168.1.20"))
                .containsExactly((byte) 192, (byte) 168, (byte) 1, (byte) 20);
        assertThat(NetUtil.createByteArrayFromIpAddressString("::1")).hasSize(16).endsWith((byte) 1);
        assertThat(NetUtil.createByteArrayFromIpAddressString("not-an-ip")).isNull();

        InetAddress v4 = NetUtil.createInetAddressFromIpAddressString("192.168.1.20");
        assertThat(v4).isInstanceOf(Inet4Address.class);
        assertThat(NetUtil.toAddressString(v4)).isEqualTo("192.168.1.20");

        InetAddress v6 = NetUtil.createInetAddressFromIpAddressString("2001:db8:0:0:1:0:0:1");
        assertThat(v6).isInstanceOf(Inet6Address.class);
        assertThat(NetUtil.toAddressString(v6)).isEqualTo("2001:db8::1:0:0:1");
        assertThat(NetUtil.createInetAddressFromIpAddressString("gateway.internal")).isNull();

        Inet6Address mapped = NetUtil.getByName("198.51.100.4", true);
        assertThat(mapped).isNotNull();
        assertThat(NetUtil.toAddressString(mapped, true)).isEqualTo("::ffff:198.51.100.4");
        assertThat(NetUtil.getByName("198.51.100.4", false)).isNull();

        assertThat(NetUtil.bytesToIpAddress(new byte[] {10, 0, 0, 42})).isEqualTo("10.0.0.42");
    }

    @Test
    void ipv4ClientsAreMatchedAgainstCidrAllowList() {
        AllowList allowList = new AllowList();
        allowList.allow("10.0.0.0/8");
        allowList.allow("192.168.10.0/24");
        allowList.allow("203.0.113.7/32");

        Map<String, Boolean> decisions = new LinkedHashMap<>();
        for (String client : new String[] {"10.250.3.9", "192.168.10.200", "192.168.11.1", "203.0.113.7", "203.0.113.8"}) {
            decisions.put(client, allowList.permits(client));
        }

        assertThat(decisions).containsExactly(
                Map.entry("10.250.3.9", true),
                Map.entry("192.168.10.200", true),
                Map.entry("192.168.11.1", false),
                Map.entry("203.0.113.7", true),
                Map.entry("203.0.113.8", false));
        assertThat(allowList.permits("2001:db8::1")).isFalse();
        assertThat(allowList.firstAddressOf("192.168.10.0/24")).isEqualTo("192.168.10.0");
        assertThat(allowList.lastAddressOf("192.168.10.0/24")).isEqualTo("192.168.10.255");
    }

    @Test
    void accessLogEntriesBracketIpv6Hosts() {
        InetSocketAddress v4Client = new InetSocketAddress(NetUtil.createInetAddressFromIpAddressString("198.51.100.4"), 51234);
        InetSocketAddress v6Client = new InetSocketAddress(NetUtil.createInetAddressFromIpAddressString("2001:db8::7"), 443);
        InetSocketAddress upstream = InetSocketAddress.createUnresolved("backend.internal", 8443);

        assertThat(NetUtil.toSocketAddressString(v4Client)).isEqualTo("198.51.100.4:51234");
        assertThat(NetUtil.toSocketAddressString(v6Client)).isEqualTo("[2001:db8::7]:443");
        assertThat(NetUtil.toSocketAddressString("2001:db8::9", 80)).isEqualTo("[2001:db8::9]:80");
        assertThat(NetUtil.toSocketAddressString(upstream)).isEqualTo("backend.internal:8443");
        assertThat(NetUtil.getHostname(upstream)).isEqualTo("backend.internal");
        assertThat(upstream.isUnresolved()).isTrue();
    }

    @Test
    void loopbackConstantsDescribeTheLocalHost() {
        assertThat(NetUtil.LOCALHOST4.isLoopbackAddress()).isTrue();
        assertThat(NetUtil.toAddressString(NetUtil.LOCALHOST4)).isEqualTo("127.0.0.1");
        assertThat(NetUtil.toAddressString(NetUtil.LOCALHOST6)).isEqualTo("::1");
        assertThat(NetUtil.LOCALHOST.isLoopbackAddress()).isTrue();
        assertThat(NetUtil.SOMAXCONN).isPositive();
        assertThat(NetUtil.NETWORK_INTERFACES).isNotNull();
    }

    /// Turns a forwarded-for header into rate-limit keys, keeping the entries it rejected.
    static final class ClientAddressNormalizer {

        private final List<String> rejected = new ArrayList<>();

        List<String> rateLimitKeys(String forwardedFor) {
            List<String> keys = new ArrayList<>();
            for (String entry : forwardedFor.split(",")) {
                String candidate = entry.trim();
                Optional<String> key = canonicalize(candidate);
                if (key.isPresent()) {
                    keys.add(key.get());
                } else {
                    rejected.add(candidate);
                }
            }
            return keys;
        }

        List<String> rejected() {
            return rejected;
        }

        private static Optional<String> canonicalize(String candidate) {
            if (!NetUtil.isValidIpV4Address(candidate) && !NetUtil.isValidIpV6Address(candidate)) {
                return Optional.empty();
            }
            InetAddress address = NetUtil.createInetAddressFromIpAddressString(candidate);
            if (address == null) {
                return Optional.empty();
            }
            if (address instanceof Inet4Address && NetUtil.isValidIpV6Address(candidate)) {
                // The JDK collapses `::ffff:a.b.c.d` to IPv4; keep the mapped form the client sent.
                return Optional.of(NetUtil.toAddressString(NetUtil.getByName(candidate, true), true));
            }
            return Optional.of(NetUtil.toAddressString(address));
        }
    }

    /// Holds IPv4 CIDR ranges as integer network/mask pairs.
    static final class AllowList {

        private final List<int[]> ranges = new ArrayList<>();

        void allow(String cidr) {
            ranges.add(parse(cidr));
        }

        boolean permits(String client) {
            if (!NetUtil.isValidIpV4Address(client)) {
                return false;
            }
            int address = NetUtil.ipv4AddressToInt((Inet4Address) NetUtil.createInetAddressFromIpAddressString(client));
            for (int[] range : ranges) {
                if ((address & range[1]) == range[0]) {
                    return true;
                }
            }
            return false;
        }

        String firstAddressOf(String cidr) {
            return NetUtil.intToIpAddress(parse(cidr)[0]);
        }

        String lastAddressOf(String cidr) {
            int[] range = parse(cidr);
            return NetUtil.intToIpAddress(range[0] | ~range[1]);
        }

        private static int[] parse(String cidr) {
            int slash = cidr.indexOf('/');
            String network = cidr.substring(0, slash);
            int prefix = Integer.parseInt(cidr.substring(slash + 1));
            if (!NetUtil.isValidIpV4Address(network)) {
                throw new IllegalArgumentException("Not an IPv4 CIDR range: " + cidr);
            }
            int mask = prefix == 0 ? 0 : -1 << (32 - prefix);
            int base = NetUtil.ipv4AddressToInt((Inet4Address) NetUtil.createInetAddressFromIpAddressString(network));
            return new int[] {base & mask, mask};
        }
    }
}
