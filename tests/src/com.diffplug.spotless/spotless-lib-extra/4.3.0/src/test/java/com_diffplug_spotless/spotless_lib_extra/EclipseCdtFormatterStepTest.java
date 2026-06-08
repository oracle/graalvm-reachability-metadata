/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_spotless.spotless_lib_extra;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.diffplug.spotless.FormatterStep;
import com.diffplug.spotless.Provisioner;
import com.diffplug.spotless.extra.EquoBasedStepBuilder;
import com.diffplug.spotless.extra.P2Provisioner;
import com.diffplug.spotless.extra.cpp.EclipseCdtFormatterStep;

public class EclipseCdtFormatterStepTest {
    private static final String CDT_FORMATTER_CLASS = "com/diffplug/spotless/extra/glue/cdt/"
            + "EclipseCdtFormatterStepImpl.class";
    private static final String STUB_CDT_FORMATTER_CLASS = """
            yv66vgAAADQAGgoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClW
            CAAIAQAVaW50IG1haW4oKXtyZXR1cm4gMDt9CAAKAQAcaW50IG1haW4oKSB7CiAgICByZXR1cm4g
            MDsKfQoADAANBwAODAAPABABABBqYXZhL2xhbmcvU3RyaW5nAQAHcmVwbGFjZQEARChMamF2YS9s
            YW5nL0NoYXJTZXF1ZW5jZTtMamF2YS9sYW5nL0NoYXJTZXF1ZW5jZTspTGphdmEvbGFuZy9TdHJp
            bmc7BwASAQBAY29tL2RpZmZwbHVnL3Nwb3RsZXNzL2V4dHJhL2dsdWUvY2R0L0VjbGlwc2VDZHRG
            b3JtYXR0ZXJTdGVwSW1wbAEAGShMamF2YS91dGlsL1Byb3BlcnRpZXM7KVYBAARDb2RlAQAPTGlu
            ZU51bWJlclRhYmxlAQAGZm9ybWF0AQAmKExqYXZhL2xhbmcvU3RyaW5nOylMamF2YS9sYW5nL1N0
            cmluZzsBAApTb3VyY2VGaWxlAQAgRWNsaXBzZUNkdEZvcm1hdHRlclN0ZXBJbXBsLmphdmEAIQAR
            AAIAAAAAAAIAAQAFABMAAQAUAAAAIQABAAIAAAAFKrcAAbEAAAABABUAAAAKAAIAAAAGAAQABwAB
            ABYAFwABABQAAAAhAAMAAgAAAAkrEgcSCbYAC7AAAAABABUAAAAGAAEAAAAKAAEAGAAAAAIAGQ==
            """;
    private static final String ECLIPSE_DOWNLOADS = "https://download.eclipse.org/";

    @TempDir
    private Path tempDir;

    @Test
    void cdtFormatterUsesReflectiveConstructorAndFormatMethod() throws Exception {
        try {
            final String cdtVersion = EclipseCdtFormatterStep.defaultVersion();
            final Path p2Repository = tempDir.resolve("p2");
            createMinimalP2Repository(p2Repository, cdtVersion);

            try (LocalP2Server p2Server = new LocalP2Server(p2Repository)) {
                final EquoBasedStepBuilder builder = EclipseCdtFormatterStep.createBuilder(
                        new StubFormatterProvisioner(tempDir), stubP2Provisioner());
                builder.setVersion(cdtVersion);
                builder.setP2Mirrors(Map.of(ECLIPSE_DOWNLOADS, p2Server.url()));
                final FormatterStep formatter = builder.build();

                final String formatted = formatter.format(
                        "int main(){return 0;}", tempDir.resolve("main.cpp").toFile());

                assertTrue(formatted.contains("int main() {"), formatted);
                assertTrue(formatted.contains("return 0;"), formatted);
            }
        } catch (Throwable throwable) {
            rethrowIfNotNativeImageDynamicClassLoadingError(throwable);
        }
    }

    private static void createMinimalP2Repository(Path p2Repository, String cdtVersion) throws IOException {
        writeContentXml(p2Repository.resolve("eclipse/updates/4.26/content.xml"));
        writeContentXml(p2Repository.resolve("tools/cdt/releases/" + cdtVersion + "/content.xml"));
    }

    private static void writeContentXml(Path contentXml) throws IOException {
        Files.createDirectories(contentXml.getParent());
        Files.writeString(contentXml, """
                <repository>
                  <units size="5">
                    <unit id="org.apache.felix.scr" version="1.0.0"/>
                    <unit id="org.eclipse.equinox.event" version="1.0.0"/>
                    <unit id="org.osgi.service.cm" version="1.0.0"/>
                    <unit id="org.osgi.service.metatype" version="1.0.0"/>
                    <unit id="org.eclipse.cdt.core" version="1.0.0"/>
                  </units>
                </repository>
                """);
    }

    private static void rethrowIfNotNativeImageDynamicClassLoadingError(Throwable throwable) {
        NativeImageDynamicClassLoadingSupport.rethrowIfNotNativeImageDynamicClassLoadingError(throwable);
    }

    private static P2Provisioner stubP2Provisioner() {
        return (modelWrapper, mavenProvisioner, cacheDirectory) -> List.copyOf(
                mavenProvisioner.provisionWithTransitives(false, Collections.emptyList()));
    }

    private static final class LocalP2Server implements AutoCloseable {
        private final Path root;
        private final HttpServer server;
        private final ExecutorService executor;

        private LocalP2Server(Path root) throws IOException {
            this.root = root;
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            this.executor = Executors.newSingleThreadExecutor();
            server.createContext("/", this::serve);
            server.setExecutor(executor);
            server.start();
        }

        private String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
        }

        private void serve(HttpExchange exchange) throws IOException {
            final Path requested = root.resolve(exchange.getRequestURI().getPath().substring(1)).normalize();
            if (!requested.startsWith(root) || !Files.isRegularFile(requested)) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }

            final byte[] content = Files.readAllBytes(requested);
            exchange.sendResponseHeaders(200, content.length);
            exchange.getResponseBody().write(content);
            exchange.close();
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }

    private static final class StubFormatterProvisioner implements Provisioner {
        private final Path outputDirectory;

        private StubFormatterProvisioner(Path outputDirectory) {
            this.outputDirectory = outputDirectory;
        }

        @Override
        public Set<File> provisionWithTransitives(boolean withTransitives, Collection<String> mavenCoordinates) {
            return Collections.singleton(createStubFormatterJar());
        }

        private File createStubFormatterJar() {
            final Path jar = outputDirectory.resolve("stub-eclipse-cdt-formatter.jar");
            if (Files.isRegularFile(jar)) {
                return jar.toFile();
            }
            try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
                output.putNextEntry(new JarEntry(CDT_FORMATTER_CLASS));
                output.write(Base64.getMimeDecoder().decode(STUB_CDT_FORMATTER_CLASS));
                output.closeEntry();
            } catch (IOException exception) {
                throw new UncheckedIOException("Unable to create Eclipse CDT formatter test JAR", exception);
            }
            return jar.toFile();
        }
    }
}
