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
import com.diffplug.spotless.extra.groovy.GrEclipseFormatterStep;

public class GrEclipseFormatterStepTest {
    private static final String GROOVY_FORMATTER_CLASS = "com/diffplug/spotless/extra/glue/groovy/"
            + "GrEclipseFormatterStepImpl.class";
    private static final String STUB_GROOVY_FORMATTER_CLASS = """
            yv66vgAAADQAGgoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClW
            CAAIAQAZZGVmIGdyZWV0KCl7cHJpbnRsbiAnaGknfQgACgEAIGRlZiBncmVldCgpIHsKICAgIHBy
            aW50bG4gJ2hpJwp9CgAMAA0HAA4MAA8AEAEAEGphdmEvbGFuZy9TdHJpbmcBAAdyZXBsYWNlAQBE
            KExqYXZhL2xhbmcvQ2hhclNlcXVlbmNlO0xqYXZhL2xhbmcvQ2hhclNlcXVlbmNlOylMamF2YS9s
            YW5nL1N0cmluZzsHABIBAEJjb20vZGlmZnBsdWcvc3BvdGxlc3MvZXh0cmEvZ2x1ZS9ncm9vdnkv
            R3JFY2xpcHNlRm9ybWF0dGVyU3RlcEltcGwBABkoTGphdmEvdXRpbC9Qcm9wZXJ0aWVzOylWAQAE
            Q29kZQEAD0xpbmVOdW1iZXJUYWJsZQEABmZvcm1hdAEAJihMamF2YS9sYW5nL1N0cmluZzspTGph
            dmEvbGFuZy9TdHJpbmc7AQAKU291cmNlRmlsZQEAH0dyRWNsaXBzZUZvcm1hdHRlclN0ZXBJbXBs
            LmphdmEAIQARAAIAAAAAAAIAAQAFABMAAQAUAAAAIQABAAIAAAAFKrcAAbEAAAABABUAAAAKAAIA
            AAAGAAQABwABABYAFwABABQAAAAhAAMAAgAAAAkrEgcSCbYAC7AAAAABABUAAAAGAAEAAAAKAAEA
            GAAAAAIAGQ==
            """;
    private static final String ECLIPSE_DOWNLOADS = "https://download.eclipse.org/";
    private static final String GROOVY_RELEASES = "https://groovy.jfrog.io/artifactory/plugins-release/";

    @TempDir
    private Path tempDir;

    @Test
    void groovyFormatterUsesReflectiveConstructorAndFormatMethod() throws Exception {
        try {
            final String eclipseVersion = GrEclipseFormatterStep.defaultVersion();
            final Path p2Repository = tempDir.resolve("p2");
            createMinimalP2Repository(p2Repository, eclipseVersion);

            try (LocalP2Server p2Server = new LocalP2Server(p2Repository)) {
                final EquoBasedStepBuilder builder = GrEclipseFormatterStep.createBuilder(
                        new StubFormatterProvisioner(tempDir));
                builder.setVersion(eclipseVersion);
                builder.setP2Mirrors(Map.of(
                        ECLIPSE_DOWNLOADS, p2Server.url(),
                        GROOVY_RELEASES, p2Server.url()));
                final FormatterStep formatter = builder.build();

                final String formatted = formatter.format(
                        "def greet(){println 'hi'}", tempDir.resolve("script.groovy").toFile());

                assertTrue(formatted.contains("def greet() {"), formatted);
                assertTrue(formatted.contains("println 'hi'"), formatted);
            }
        } catch (Throwable throwable) {
            rethrowIfNotNativeImageDynamicClassLoadingError(throwable);
        }
    }

    private static void createMinimalP2Repository(Path p2Repository, String eclipseVersion) throws IOException {
        writeContentXml(p2Repository.resolve("eclipse/updates/" + eclipseVersion + "/content.xml"));
        writeContentXml(p2Repository.resolve("org/codehaus/groovy/groovy-eclipse-integration/"
                + groovyEclipseVersion(eclipseVersion) + "/e" + eclipseVersion + "/content.xml"));
    }

    private static String groovyEclipseVersion(String eclipseVersion) {
        final int eclipseMinorVersion = Integer.parseInt(eclipseVersion.substring("4.".length()));
        if (eclipseMinorVersion >= 28) {
            return "5." + (eclipseMinorVersion - 28) + ".0";
        }
        if (eclipseMinorVersion >= 18) {
            return "4." + (eclipseMinorVersion - 18) + ".0";
        }
        return "3." + (eclipseMinorVersion - 8) + ".0";
    }

    private static void writeContentXml(Path contentXml) throws IOException {
        Files.createDirectories(contentXml.getParent());
        Files.writeString(contentXml, """
                <repository>
                  <units size="8">
                    <unit id="org.apache.felix.scr" version="1.0.0"/>
                    <unit id="org.eclipse.equinox.event" version="1.0.0"/>
                    <unit id="org.osgi.service.cm" version="1.0.0"/>
                    <unit id="org.osgi.service.metatype" version="1.0.0"/>
                    <unit id="org.codehaus.groovy.eclipse.refactoring" version="1.0.0"/>
                    <unit id="org.codehaus.groovy.eclipse.core" version="1.0.0"/>
                    <unit id="org.eclipse.jdt.groovy.core" version="1.0.0"/>
                    <unit id="org.codehaus.groovy" version="1.0.0"/>
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
            final Path jar = outputDirectory.resolve("stub-greclipse-formatter.jar");
            if (Files.isRegularFile(jar)) {
                return jar.toFile();
            }
            try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
                output.putNextEntry(new JarEntry(GROOVY_FORMATTER_CLASS));
                output.write(Base64.getMimeDecoder().decode(STUB_GROOVY_FORMATTER_CLASS));
                output.closeEntry();
            } catch (IOException exception) {
                throw new UncheckedIOException("Unable to create GrEclipse formatter test JAR", exception);
            }
            return jar.toFile();
        }
    }
}
