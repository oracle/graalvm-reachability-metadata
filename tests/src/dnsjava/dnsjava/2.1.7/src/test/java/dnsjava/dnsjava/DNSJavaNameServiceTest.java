/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dnsjava.dnsjava;

import java.lang.reflect.Proxy;
import java.net.InetAddress;

import org.junit.jupiter.api.Test;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.Cache;
import org.xbill.DNS.Credibility;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Name;
import org.xbill.DNS.Type;
import org.xbill.DNS.spi.DNSJavaNameService;

import static org.assertj.core.api.Assertions.assertThat;

public class DNSJavaNameServiceTest {
    private static final String PREFER_IPV6_PROPERTY = "java.net.preferIPv6Addresses";
    private static final Name HOST = Name.fromConstantString("service.example.");

    @Test
    void proxyLookupReturnsInetAddressArrayFromCachedDnsRecord() throws Exception {
        withCachedAddress(() -> {
            ModernNameService nameService = newModernProxy();

            InetAddress expectedAddress = ipv4Address();
            InetAddress[] addresses = nameService.lookupAllHostAddr(HOST.toString());

            assertThat(addresses).containsExactly(expectedAddress);
        });
    }

    @Test
    void proxyLookupReturnsLegacyByteAddressArrayFromCachedDnsRecord() throws Exception {
        withCachedAddress(() -> {
            LegacyNameService nameService = newLegacyProxy();

            byte[][] addresses = nameService.lookupAllHostAddr(HOST.toString());

            byte[] expectedAddress = ipv4AddressBytes();
            assertThat(addresses).hasDimensions(1, expectedAddress.length);
            assertThat(addresses[0]).containsExactly(expectedAddress);
        });
    }

    private static ModernNameService newModernProxy() {
        Object proxy = Proxy.newProxyInstance(
                ModernNameService.class.getClassLoader(),
                new Class<?>[] {
                        ModernNameService.class
                },
                new TestableDNSJavaNameService());
        return (ModernNameService) proxy;
    }

    private static LegacyNameService newLegacyProxy() {
        Object proxy = Proxy.newProxyInstance(
                LegacyNameService.class.getClassLoader(),
                new Class<?>[] {
                        LegacyNameService.class
                },
                new TestableDNSJavaNameService());
        return (LegacyNameService) proxy;
    }

    private static void withCachedAddress(ThrowingRunnable test) throws Exception {
        Cache previousARecords = Lookup.getDefaultCache(Type.A);
        String previousPreferIpv6 = System.getProperty(PREFER_IPV6_PROPERTY);
        Cache cache = new Cache();
        ARecord record = new ARecord(HOST, DClass.IN, 300, ipv4Address());
        cache.addRecord(record, Credibility.NORMAL, DNSJavaNameServiceTest.class);
        Lookup.setDefaultCache(cache, Type.A);
        System.setProperty(PREFER_IPV6_PROPERTY, "false");
        try {
            test.run();
        } finally {
            restoreProperty(PREFER_IPV6_PROPERTY, previousPreferIpv6);
            Lookup.setDefaultCache(previousARecords, Type.A);
        }
    }

    private static InetAddress ipv4Address() throws Exception {
        return InetAddress.getByAddress(ipv4AddressBytes());
    }

    private static byte[] ipv4AddressBytes() {
        return new byte[] {
                (byte) 192, 0, 2, 42
        };
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    public interface ModernNameService {
        InetAddress[] lookupAllHostAddr(String host) throws Exception;
    }

    public interface LegacyNameService {
        byte[][] lookupAllHostAddr(String host) throws Exception;
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class TestableDNSJavaNameService extends DNSJavaNameService {
    }
}
