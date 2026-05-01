/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import org.apache.log4j.net.ZeroConfSupport;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ZeroConfSupportTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void advertisesAndUnadvertisesServiceThroughJmDnsVersion3Api() {
        JmDNS jmDNS = (JmDNS) ZeroConfSupport.getJMDNSInstance();
        jmDNS.clear();

        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("endpoint", "integration-test");
        properties.put("transport", "tcp");

        ZeroConfSupport zeroConfSupport = new ZeroConfSupport("_log4j._tcp.local.", 4560, "reload4j-test", properties);

        zeroConfSupport.advertise();

        assertThat(jmDNS.getRegisteredServices()).hasSize(1);
        ServiceInfo serviceInfo = jmDNS.getRegisteredServices().get(0);
        assertThat(serviceInfo.getType()).isEqualTo("_log4j._tcp.local.");
        assertThat(serviceInfo.getName()).isEqualTo("reload4j-test");
        assertThat(serviceInfo.getPort()).isEqualTo(4560);
        assertThat(serviceInfo.getWeight()).isZero();
        assertThat(serviceInfo.getPriority()).isZero();
        assertThat(serviceInfo.getProperties()).containsExactlyEntriesOf(properties);

        zeroConfSupport.unadvertise();

        assertThat(jmDNS.getRegisteredServices()).isEmpty();
        assertThat(jmDNS.getUnregisteredServices()).containsExactly(serviceInfo);
    }

    @Test
    void advertisesServiceThroughJmDnsVersion1Api() throws Exception {
        try {
            Path classesDirectory = writeVersion1JmDnsApiClasses();
            URL reload4jUrl = reload4jCodeSourceUrl();
            URL[] urls = new URL[] { classesDirectory.toUri().toURL(), reload4jUrl };

            try (URLClassLoader classLoader = new URLClassLoader(urls, null)) {
                Class<?> zeroConfClass = Class.forName("org.apache.log4j.net.ZeroConfSupport", true, classLoader);
                Object zeroConfSupport = zeroConfClass
                        .getConstructor(String.class, int.class, String.class, Map.class)
                        .newInstance("_log4j._tcp.local.", 4561, "reload4j-v1-test", Map.of("api", "v1"));

                zeroConfClass.getMethod("advertise").invoke(zeroConfSupport);

                Object jmDns = zeroConfClass.getMethod("getJMDNSInstance").invoke(null);
                Object registeredServices = jmDns.getClass().getMethod("getRegisteredServices").invoke(jmDns);
                assertThat((List<?>) registeredServices).hasSize(1);
            }
        } catch (Error error) {
            rethrowIfNotNativeImageDynamicClassLoadingError(error);
        }
    }

    private Path writeVersion1JmDnsApiClasses() throws IOException {
        Path classesDirectory = Files.createDirectories(temporaryDirectory.resolve("jmdns-v1-classes"));
        writeClass(classesDirectory, "javax/jmdns/JmDNS.class", VERSION1_JMDNS_CLASS);
        writeClass(classesDirectory, "javax/jmdns/ServiceInfo.class", VERSION1_SERVICE_INFO_CLASS);
        return classesDirectory;
    }

    private static void writeClass(Path classesDirectory, String relativePath, String encodedClass) throws IOException {
        Path classFile = classesDirectory.resolve(relativePath);
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, Base64.getMimeDecoder().decode(encodedClass));
    }

    private static URL reload4jCodeSourceUrl() {
        CodeSource codeSource = ZeroConfSupport.class.getProtectionDomain().getCodeSource();
        assertThat(codeSource).isNotNull();
        return codeSource.getLocation();
    }

    private static final String VERSION1_JMDNS_CLASS = """
            yv66vgAAAEUAKwoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClWBwAIAQATamF2YS91dGls
            L0FycmF5TGlzdAoABwADCQALAAwHAA0MAA4ADwEAEWphdmF4L2ptZG5zL0ptRE5TAQAScmVnaXN0ZXJlZFNlcnZpY2VzAQAQ
            TGphdmEvdXRpbC9MaXN0OwsAEQASBwATDAAUABUBAA5qYXZhL3V0aWwvTGlzdAEAA2FkZAEAFShMamF2YS9sYW5nL09iamVj
            dDspWgsAEQAXDAAYABUBAAZyZW1vdmUKABoAGwcAHAwAHQAeAQAVamF2YS91dGlsL0NvbGxlY3Rpb25zAQAQdW5tb2RpZmlh
            YmxlTGlzdAEAIihMamF2YS91dGlsL0xpc3Q7KUxqYXZhL3V0aWwvTGlzdDsBAAlTaWduYXR1cmUBACtMamF2YS91dGlsL0xp
            c3Q8TGphdmF4L2ptZG5zL1NlcnZpY2VJbmZvOz47AQAEQ29kZQEAD0xpbmVOdW1iZXJUYWJsZQEAD3JlZ2lzdGVyU2Vydmlj
            ZQEAHChMamF2YXgvam1kbnMvU2VydmljZUluZm87KVYBABF1bnJlZ2lzdGVyU2VydmljZQEAFWdldFJlZ2lzdGVyZWRTZXJ2
            aWNlcwEAEigpTGphdmEvdXRpbC9MaXN0OwEALSgpTGphdmEvdXRpbC9MaXN0PExqYXZheC9qbWRucy9TZXJ2aWNlSW5mbzs+
            OwEAClNvdXJjZUZpbGUBAApKbUROUy5qYXZhACEACwACAAAAAQASAA4ADwABAB8AAAACACAABAABAAUABgABACEAAAAwAAMA
            AQAAABAqtwABKrsAB1m3AAm1AAqxAAAAAQAiAAAADgADAAAACgAEAAgADwALAAEAIwAkAAEAIQAAACgAAgACAAAADCq0AAor
            uQAQAgBXsQAAAAEAIgAAAAoAAgAAAA4ACwAPAAEAJQAkAAEAIQAAACgAAgACAAAADCq0AAoruQAWAgBXsQAAAAEAIgAAAAoA
            AgAAABIACwATAAEAJgAnAAIAIQAAACAAAQABAAAACCq0AAq4ABmwAAAAAQAiAAAABgABAAAAFgAfAAAAAgAoAAEAKQAAAAIA
            Kg==
            """;

    private static final String VERSION1_SERVICE_INFO_CLASS = """
            yv66vgAAAEUAOwoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClWCQAIAAkHAAoMAAsADAEA
            F2phdmF4L2ptZG5zL1NlcnZpY2VJbmZvAQAEdHlwZQEAEkxqYXZhL2xhbmcvU3RyaW5nOwkACAAODAAPAAwBAARuYW1lCQAI
            ABEMABIAEwEABHBvcnQBAAFJCQAIABUMABYAFwEACnByb3BlcnRpZXMBABVMamF2YS91dGlsL0hhc2h0YWJsZTsKABkAGgcA
            GwwAHAAdAQAQamF2YS9sYW5nL1N0cmluZwEAB3ZhbHVlT2YBACYoTGphdmEvbGFuZy9PYmplY3Q7KUxqYXZhL2xhbmcvU3Ry
            aW5nOxIAAAAfDAAgACEBABdtYWtlQ29uY2F0V2l0aENvbnN0YW50cwEASyhMamF2YS9sYW5nL1N0cmluZztMamF2YS9sYW5n
            L1N0cmluZztJTGphdmEvbGFuZy9TdHJpbmc7KUxqYXZhL2xhbmcvU3RyaW5nOwEACVNpZ25hdHVyZQEAGUxqYXZhL3V0aWwv
            SGFzaHRhYmxlPCoqPjsBAD8oTGphdmEvbGFuZy9TdHJpbmc7TGphdmEvbGFuZy9TdHJpbmc7SUlJTGphdmEvdXRpbC9IYXNo
            dGFibGU7KVYBAARDb2RlAQAPTGluZU51bWJlclRhYmxlAQBDKExqYXZhL2xhbmcvU3RyaW5nO0xqYXZhL2xhbmcvU3RyaW5n
            O0lJSUxqYXZhL3V0aWwvSGFzaHRhYmxlPCoqPjspVgEACHRvU3RyaW5nAQAUKClMamF2YS9sYW5nL1N0cmluZzsBAApTb3Vy
            Y2VGaWxlAQAQU2VydmljZUluZm8uamF2YQEAEEJvb3RzdHJhcE1ldGhvZHMIAC4BAAcBOgE6AToBDwYAMAoAMQAyBwAzDAAg
            ADQBACRqYXZhL2xhbmcvaW52b2tlL1N0cmluZ0NvbmNhdEZhY3RvcnkBAJgoTGphdmEvbGFuZy9pbnZva2UvTWV0aG9kSGFu
            ZGxlcyRMb29rdXA7TGphdmEvbGFuZy9TdHJpbmc7TGphdmEvbGFuZy9pbnZva2UvTWV0aG9kVHlwZTtMamF2YS9sYW5nL1N0
            cmluZztbTGphdmEvbGFuZy9PYmplY3Q7KUxqYXZhL2xhbmcvaW52b2tlL0NhbGxTaXRlOwEADElubmVyQ2xhc3NlcwcANwEA
            JWphdmEvbGFuZy9pbnZva2UvTWV0aG9kSGFuZGxlcyRMb29rdXAHADkBAB5qYXZhL2xhbmcvaW52b2tlL01ldGhvZEhhbmRs
            ZXMBAAZMb29rdXAAIQAIAAIAAAAEABIACwAMAAAAEgAPAAwAAAASABIAEwAAABIAFgAXAAEAIgAAAAIAIwACAAEABQAkAAIA
            JQAAAEYAAgAHAAAAGiq3AAEqK7UAByostQANKh21ABAqGQa1ABSxAAAAAQAmAAAAGgAGAAAADAAEAA0ACQAOAA4ADwATABAA
            GQARACIAAAACACcAAQAoACkAAQAlAAAAMQAEAAEAAAAZKrQAByq0AA0qtAAQKrQAFLgAGLoAHgAAsAAAAAEAJgAAAAYAAQAA
            ABUAAwAqAAAAAgArACwAAAAIAAEALwABAC0ANQAAAAoAAQA2ADgAOgAZ
            """;

    private static void rethrowIfNotNativeImageDynamicClassLoadingError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }
}
