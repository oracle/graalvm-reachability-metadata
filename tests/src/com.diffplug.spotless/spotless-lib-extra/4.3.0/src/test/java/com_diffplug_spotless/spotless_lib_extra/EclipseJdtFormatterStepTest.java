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
import com.diffplug.spotless.extra.java.EclipseJdtFormatterStep;

public class EclipseJdtFormatterStepTest {
    private static final String JDT_FORMATTER_CLASS = "com/diffplug/spotless/extra/glue/jdt/"
            + "EclipseJdtFormatterStepImpl.class";
    private static final String STUB_JDT_FORMATTER_CLASS = """
            yv66vgAAADQAHAoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClW
            CAAIAQAuY2xhc3MgRGVtb3t2b2lkIHJ1bigpe1N5c3RlbS5vdXQucHJpbnRsbigxKTt9fQgACgEA
            RWNsYXNzIERlbW8gewogICAgdm9pZCBydW4oKSB7CiAgICAgICAgU3lzdGVtLm91dC5wcmludGxu
            KDEpOwogICAgfQp9CgoADAANBwAODAAPABABABBqYXZhL2xhbmcvU3RyaW5nAQAHcmVwbGFjZQEA
            RChMamF2YS9sYW5nL0NoYXJTZXF1ZW5jZTtMamF2YS9sYW5nL0NoYXJTZXF1ZW5jZTspTGphdmEv
            bGFuZy9TdHJpbmc7BwASAQBAY29tL2RpZmZwbHVnL3Nwb3RsZXNzL2V4dHJhL2dsdWUvamR0L0Vj
            bGlwc2VKZHRGb3JtYXR0ZXJTdGVwSW1wbAEAKChMamF2YS91dGlsL1Byb3BlcnRpZXM7TGphdmEv
            dXRpbC9NYXA7KVYBAARDb2RlAQAPTGluZU51bWJlclRhYmxlAQAJU2lnbmF0dXJlAQBOKExqYXZh
            L3V0aWwvUHJvcGVydGllcztMamF2YS91dGlsL01hcDxMamF2YS9sYW5nL1N0cmluZztMamF2YS9s
            YW5nL1N0cmluZzs+OylWAQAGZm9ybWF0AQA0KExqYXZhL2xhbmcvU3RyaW5nO0xqYXZhL2lvL0Zp
            bGU7KUxqYXZhL2xhbmcvU3RyaW5nOwEAClNvdXJjZUZpbGUBACBFY2xpcHNlSmR0Rm9ybWF0dGVy
            U3RlcEltcGwuamF2YQAhABEAAgAAAAAAAgABAAUAEwACABQAAAAdAAEAAwAAAAUqtwABsQAAAAEA
            FQAAAAYAAQAAAAgAFgAAAAIAFwABABgAGQABABQAAAAhAAMAAwAAAAkrEgcSCbYAC7AAAAABABUA
            AAAGAAEAAAALAAEAGgAAAAIAGw==
            """;
    private static final String ECLIPSE_DOWNLOADS = "https://download.eclipse.org/";

    @TempDir
    private Path tempDir;

    @Test
    void jdtFormatterUsesReflectiveConstructorAndFileAwareFormatMethod() throws Exception {
        try {
            final String jdtVersion = EclipseJdtFormatterStep.defaultVersion();
            final Path p2Repository = tempDir.resolve("p2");
            createMinimalP2Repository(p2Repository, jdtVersion);

            try (LocalP2Server p2Server = new LocalP2Server(p2Repository)) {
                final EquoBasedStepBuilder builder = EclipseJdtFormatterStep.createBuilder(
                        new StubFormatterProvisioner(tempDir));
                builder.setVersion(jdtVersion);
                builder.setP2Mirrors(Map.of(ECLIPSE_DOWNLOADS, p2Server.url()));
                final FormatterStep formatter = builder.build();

                final String formatted = formatter.format(
                        "class Demo{void run(){System.out.println(1);}}",
                        tempDir.resolve("Demo.java").toFile());

                assertTrue(formatted.contains("class Demo {"), formatted);
                assertTrue(formatted.contains("System.out.println(1);"), formatted);
            }
        } catch (Throwable throwable) {
            rethrowIfNotNativeImageDynamicClassLoadingError(throwable);
        }
    }

    private static void createMinimalP2Repository(Path p2Repository, String jdtVersion) throws IOException {
        writeContentXml(p2Repository.resolve("eclipse/updates/" + jdtVersion + "/content.xml"));
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
                    <unit id="org.eclipse.jdt.core" version="1.0.0"/>
                  </units>
                </repository>
                """);
    }

    private static void rethrowIfNotNativeImageDynamicClassLoadingError(Throwable throwable) {
        NativeImageDynamicClassLoadingSupport.rethrowIfNotNativeImageDynamicClassLoadingError(throwable);
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
            final Path jar = outputDirectory.resolve("stub-eclipse-jdt-formatter.jar");
            if (Files.isRegularFile(jar)) {
                return jar.toFile();
            }
            try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
                output.putNextEntry(new JarEntry(JDT_FORMATTER_CLASS));
                output.write(Base64.getMimeDecoder().decode(STUB_JDT_FORMATTER_CLASS));
                output.closeEntry();
            } catch (IOException exception) {
                throw new UncheckedIOException("Unable to create Eclipse JDT formatter test JAR", exception);
            }
            return jar.toFile();
        }
    }
}
