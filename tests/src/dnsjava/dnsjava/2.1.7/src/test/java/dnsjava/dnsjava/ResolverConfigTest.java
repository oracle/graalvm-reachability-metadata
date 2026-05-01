/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dnsjava.dnsjava;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.xbill.DNS.Name;
import org.xbill.DNS.ResolverConfig;

import static org.assertj.core.api.Assertions.assertThat;

public class ResolverConfigTest {
    private static final String DNS_SERVER = "192.0.2.53";
    private static final String SECONDARY_DNS_SERVER = "198.51.100.53";
    private static final String WINDOWS_IPCONFIG = """
            Windows IP Configuration

               Host Name . . . . . . . . . . . . : workstation.example.com
               Primary Dns Suffix  . . . . . . . : example.com
               DNS Suffix Search List. . . . . . : corp.example.com
                                                   lab.example.com
               DNS Servers . . . . . . . . . . . : 192.0.2.53
                                                   198.51.100.53
            """;

    @Test
    void constructorQueriesJdkResolverConfigurationWhenSystemPropertiesAreAbsent() {
        String dnsServer = System.clearProperty("dns.server");
        String dnsSearch = System.clearProperty("dns.search");
        try {
            ResolverConfig config = new ResolverConfig();

            assertThat(config.ndots()).isGreaterThanOrEqualTo(1);
        } finally {
            restoreProperty("dns.server", dnsServer);
            restoreProperty("dns.search", dnsSearch);
        }
    }

    @Test
    void windowsParserLoadsDefaultResourceBundleAndParsesDnsServers() throws Exception {
        ResolverConfig config = newEmptyResolverConfig();

        invokeWindowsParser(config, null);

        assertThat(config.servers()).containsExactly(DNS_SERVER, SECONDARY_DNS_SERVER);
        assertThat(searchPath(config))
                .contains("workstation.example.com.", "example.com.", "corp.example.com.", "lab.example.com.");
    }

    @Test
    void windowsParserLoadsLocaleSpecificResourceBundleAndParsesDnsServers() throws Exception {
        ResolverConfig config = newEmptyResolverConfig();

        invokeWindowsParser(config, Locale.ROOT);

        assertThat(config.servers()).containsExactly(DNS_SERVER, SECONDARY_DNS_SERVER);
        assertThat(searchPath(config)).contains("example.com.", "corp.example.com.");
    }

    private static ResolverConfig newEmptyResolverConfig() throws Exception {
        ResolverConfig config = new ResolverConfig();
        setField(config, "servers", null);
        setField(config, "searchlist", null);
        return config;
    }

    private static void invokeWindowsParser(ResolverConfig config, Locale locale) throws Exception {
        Method method = ResolverConfig.class.getDeclaredMethod("findWin", InputStream.class, Locale.class);
        method.setAccessible(true);
        ByteArrayInputStream input = new ByteArrayInputStream(WINDOWS_IPCONFIG.getBytes(StandardCharsets.UTF_8));
        try {
            method.invoke(config, input, locale);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new AssertionError(cause);
        }
    }

    private static void setField(ResolverConfig config, String name, Object value) throws Exception {
        Field field = ResolverConfig.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(config, value);
    }

    private static String[] searchPath(ResolverConfig config) {
        Name[] names = config.searchPath();
        return Arrays.stream(names)
                .map(Name::toString)
                .toArray(String[]::new);
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
