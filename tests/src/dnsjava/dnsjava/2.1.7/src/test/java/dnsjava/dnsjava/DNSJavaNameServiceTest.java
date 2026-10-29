/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dnsjava.dnsjava;

import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.xbill.DNS.AAAARecord;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.Cache;
import org.xbill.DNS.Credibility;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Name;
import org.xbill.DNS.PTRRecord;
import org.xbill.DNS.ReverseMap;
import org.xbill.DNS.spi.DNSJavaNameService;

import static org.assertj.core.api.Assertions.assertThat;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
public class DNSJavaNameServiceTest {
    private final Map<String, String> previousProperties = new HashMap<>();
    private Cache previousCache;
    private Name host;
    private InetAddress ipv4;
    private InetAddress ipv6;

    @BeforeEach
    public void configureCachedAnswers() throws Exception {
        for (String key : List.of("sun.net.spi.nameservice.nameservers",
                "sun.net.spi.nameservice.domain", "java.net.preferIPv6Addresses")) {
            previousProperties.put(key, System.getProperty(key));
            System.clearProperty(key);
        }
        previousCache = Lookup.getDefaultCache(DClass.IN);
        host = Name.fromString("nameservice.example.");
        ipv4 = InetAddress.getByAddress(new byte[]{(byte) 192, 0, 2, 41});
        ipv6 = InetAddress.getByAddress(new byte[]{
                0x20, 0x01, 0x0d, (byte) 0xb8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 41});
        Cache cache = new Cache(DClass.IN);
        cache.addRecord(new ARecord(host, DClass.IN, 300, ipv4), Credibility.AUTH_ANSWER, null);
        cache.addRecord(new AAAARecord(host, DClass.IN, 300, ipv6), Credibility.AUTH_ANSWER, null);
        cache.addRecord(new PTRRecord(ReverseMap.fromAddress(ipv4), DClass.IN, 300, host),
                Credibility.AUTH_ANSWER, null);
        Lookup.setDefaultCache(cache, DClass.IN);
    }

    @AfterEach
    public void restoreDefaults() {
        Lookup.setDefaultCache(previousCache, DClass.IN);
        previousProperties.forEach((key, value) -> {
            if (value == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, value);
            }
        });
    }

    @Test
    public void resolvesAddressesAndReverseNamesThroughProxy() throws Exception {
        AddressLookup service = (AddressLookup) Proxy.newProxyInstance(
                AddressLookup.class.getClassLoader(), new Class<?>[]{AddressLookup.class},
                new DNSJavaNameService() { });

        assertThat(service.lookupAllHostAddr(host.toString())).containsExactly(ipv4);
        assertThat(service.getHostByAddr(ipv4.getAddress())).isEqualTo(host.toString());
    }

    @Test
    public void honorsIpv6PreferenceThroughProxy() throws Exception {
        System.setProperty("java.net.preferIPv6Addresses", "true");
        AddressLookup service = (AddressLookup) Proxy.newProxyInstance(
                AddressLookup.class.getClassLoader(), new Class<?>[]{AddressLookup.class},
                new DNSJavaNameService() { });

        assertThat(service.lookupAllHostAddr(host.toString())).containsExactly(ipv6);
    }

    @Test
    public void returnsRawAddressesThroughByteArrayProxy() throws Exception {
        RawAddressLookup service = (RawAddressLookup) Proxy.newProxyInstance(
                RawAddressLookup.class.getClassLoader(), new Class<?>[]{RawAddressLookup.class},
                new DNSJavaNameService() { });

        byte[][] addresses = service.lookupAllHostAddr(host.toString());

        assertThat(addresses.length).isEqualTo(1);
        assertThat(addresses[0]).containsExactly(ipv4.getAddress());
    }

    public interface AddressLookup {
        InetAddress[] lookupAllHostAddr(String name) throws UnknownHostException;

        String getHostByAddr(byte[] address) throws UnknownHostException;
    }

    public interface RawAddressLookup {
        byte[][] lookupAllHostAddr(String name) throws UnknownHostException;
    }
}
