/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_bootstrap_runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import io.quarkus.bootstrap.graal.ImageInfo;
import io.quarkus.bootstrap.runner.AotQuarkusEntryPoint;
import io.quarkus.bootstrap.runner.AotSerializedApplication;
import io.quarkus.bootstrap.runner.QuarkusEntryPoint;
import io.quarkus.bootstrap.runner.SerializedApplication;
import io.quarkus.bootstrap.runner.Timing;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class BootstrapRunnerCoverageTest {
    private static final String APPLICATION_RESOURCE = "app/resource.txt";
    private static final String SERVICE_RESOURCE = "META-INF/services/example.Service";
    private static final String APPLICATION_PROPERTIES = "application.properties";
    private static final String INVOCATIONS_PROPERTY = "bootstrap.runner.coverage.invocations";
    private static final String RESOURCE_PROPERTY = "bootstrap.runner.coverage.resource";
    private static final String RESOURCE_STREAM_PROPERTY = "bootstrap.runner.coverage.resource.stream";
    private static final String RESOURCE_COUNT_PROPERTY = "bootstrap.runner.coverage.resource.count";
    private static final String SERVICE_PROPERTY = "bootstrap.runner.coverage.service";
    private static final String CONFIG_COUNT_PROPERTY = "bootstrap.runner.coverage.config.count";

    @TempDir
    Path temporaryDirectory;

    @Test
    void entryPointsLoadSerializedApplicationsAndInvokeMainClasses() throws Throwable {
        assumeFalse(ImageInfo.inImageRuntimeCode(), "URLClassLoader-based bootstrap fixture is JVM-only");

        String originalInvocations = System.getProperty(INVOCATIONS_PROPERTY);
        String originalResource = System.getProperty(RESOURCE_PROPERTY);
        String originalResourceStream = System.getProperty(RESOURCE_STREAM_PROPERTY);
        String originalResourceCount = System.getProperty(RESOURCE_COUNT_PROPERTY);
        String originalService = System.getProperty(SERVICE_PROPERTY);
        String originalConfigCount = System.getProperty(CONFIG_COUNT_PROPERTY);
        String originalLoggingManager = System.getProperty("java.util.logging.manager");
        String originalForkJoinFactory = System.getProperty("java.util.concurrent.ForkJoinPool.common.threadFactory");
        try {
            Path applicationJar = createApplicationJar(temporaryDirectory.resolve("test-application.jar"));

            invokeStandardEntryPoint(applicationJar);
            assertBootstrapApplicationInvocation("standard");

            Timing.staticInitStarted(BootstrapRunnerCoverageTest.class.getClassLoader(), false);
            Timing.restart(BootstrapRunnerCoverageTest.class.getClassLoader());

            clearBootstrapApplicationProperties();

            invokeAotEntryPoint(applicationJar);
            assertBootstrapApplicationInvocation("aot");
        } finally {
            restoreProperty(INVOCATIONS_PROPERTY, originalInvocations);
            restoreProperty(RESOURCE_PROPERTY, originalResource);
            restoreProperty(RESOURCE_STREAM_PROPERTY, originalResourceStream);
            restoreProperty(RESOURCE_COUNT_PROPERTY, originalResourceCount);
            restoreProperty(SERVICE_PROPERTY, originalService);
            restoreProperty(CONFIG_COUNT_PROPERTY, originalConfigCount);
            restoreProperty("java.util.logging.manager", originalLoggingManager);
            restoreProperty("java.util.concurrent.ForkJoinPool.common.threadFactory", originalForkJoinFactory);
        }
    }

    private void invokeStandardEntryPoint(Path applicationJar) throws Throwable {
        Path applicationRoot = temporaryDirectory.resolve("standard-app");
        Path runnerJar = copyRunnerJar(applicationRoot);
        Path applicationData = applicationRoot.resolve(QuarkusEntryPoint.QUARKUS_APPLICATION_DAT);
        Files.createDirectories(applicationData.getParent());
        try (OutputStream outputStream = Files.newOutputStream(applicationData)) {
            SerializedApplication.write(outputStream, TestBootstrapApplication.class.getName(), applicationRoot,
                    List.of(applicationJar), List.of());
        }

        invokeEntryPoint(runnerJar, QuarkusEntryPoint.class.getName(), List.of(), "standard");
    }

    private void invokeAotEntryPoint(Path applicationJar) throws Throwable {
        Path applicationRoot = temporaryDirectory.resolve("aot-app");
        Path runnerJar = copyRunnerJar(applicationRoot);
        Path applicationData = applicationRoot.resolve(AotQuarkusEntryPoint.QUARKUS_APPLICATION_DAT);
        Files.createDirectories(applicationData.getParent());
        try (OutputStream outputStream = Files.newOutputStream(applicationData)) {
            AotSerializedApplication.write(outputStream, TestBootstrapApplication.class.getName(),
                    List.of(applicationJar));
        }

        invokeEntryPoint(runnerJar, AotQuarkusEntryPoint.class.getName(), List.of(applicationJar), "aot");
    }

    private Path copyRunnerJar(Path applicationRoot) throws IOException, URISyntaxException {
        URL runnerJarUrl = QuarkusEntryPoint.class.getProtectionDomain().getCodeSource().getLocation();
        Path sourceRunnerJar = Path.of(runnerJarUrl.toURI());
        Path runnerJar = applicationRoot.resolve("lib/main/quarkus-bootstrap-runner.jar");
        Files.createDirectories(runnerJar.getParent());
        Files.copy(sourceRunnerJar, runnerJar);
        return runnerJar;
    }

    private void invokeEntryPoint(Path runnerJar, String entryPointClassName, List<Path> additionalClasspath, String mode)
            throws Throwable {
        List<URL> urls = new ArrayList<>();
        urls.add(runnerJar.toUri().toURL());
        for (Path path : additionalClasspath) {
            urls.add(path.toUri().toURL());
        }
        for (String entry : System.getProperty("java.class.path").split(System.getProperty("path.separator"))) {
            if (!entry.isBlank()) {
                urls.add(Path.of(entry).toUri().toURL());
            }
        }

        try (URLClassLoader entryPointClassLoader = new URLClassLoader(urls.toArray(URL[]::new), null)) {
            Class<?> entryPoint = entryPointClassLoader.loadClass(entryPointClassName);
            Method main = entryPoint.getDeclaredMethod("doRun", String[].class);
            main.setAccessible(true);
            try {
                main.invoke(null, (Object) new String[] {mode});
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    private Path createApplicationJar(Path jarPath) throws IOException {
        try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath))) {
            addClass(jarOutputStream, TestBootstrapApplication.class);
            addTextEntry(jarOutputStream, APPLICATION_RESOURCE, "runner-resource");
            addTextEntry(jarOutputStream, SERVICE_RESOURCE, "example.ServiceImpl");
            addTextEntry(jarOutputStream, APPLICATION_PROPERTIES, "runner.coverage=true");
        }
        return jarPath;
    }

    private void addClass(JarOutputStream jarOutputStream, Class<?> type) throws IOException {
        String resourceName = type.getName().replace('.', '/') + ".class";
        try (InputStream inputStream = type.getClassLoader().getResourceAsStream(resourceName)) {
            if (inputStream == null) {
                throw new IOException("Unable to locate class bytes for " + type.getName());
            }
            addEntry(jarOutputStream, resourceName, inputStream.readAllBytes());
        }
    }

    private void addTextEntry(JarOutputStream jarOutputStream, String name, String content) throws IOException {
        addEntry(jarOutputStream, name, content.getBytes(StandardCharsets.UTF_8));
    }

    private void addEntry(JarOutputStream jarOutputStream, String name, byte[] content) throws IOException {
        jarOutputStream.putNextEntry(new JarEntry(name));
        jarOutputStream.write(content);
        jarOutputStream.closeEntry();
    }

    private void assertBootstrapApplicationInvocation(String expectedMode) {
        assertThat(System.getProperty(INVOCATIONS_PROPERTY)).isEqualTo(expectedMode);
        assertThat(System.getProperty(RESOURCE_PROPERTY)).isEqualTo("runner-resource");
        assertThat(System.getProperty(RESOURCE_STREAM_PROPERTY)).isEqualTo("runner-resource");
        assertThat(System.getProperty(RESOURCE_COUNT_PROPERTY)).isEqualTo("1");
        assertThat(System.getProperty(SERVICE_PROPERTY)).isEqualTo("example.ServiceImpl");
        assertThat(System.getProperty(CONFIG_COUNT_PROPERTY)).isEqualTo("1");
    }

    private void clearBootstrapApplicationProperties() {
        System.clearProperty(INVOCATIONS_PROPERTY);
        System.clearProperty(RESOURCE_PROPERTY);
        System.clearProperty(RESOURCE_STREAM_PROPERTY);
        System.clearProperty(RESOURCE_COUNT_PROPERTY);
        System.clearProperty(SERVICE_PROPERTY);
        System.clearProperty(CONFIG_COUNT_PROPERTY);
    }

    private void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    public static class TestBootstrapApplication {
        public static void main(String[] args) throws IOException {
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            System.setProperty(INVOCATIONS_PROPERTY, args[0]);
            URL resource = contextClassLoader.getResource(APPLICATION_RESOURCE);
            try (InputStream inputStream = resource.openStream()) {
                System.setProperty(RESOURCE_PROPERTY, new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
            }
            try (InputStream inputStream = contextClassLoader.getResourceAsStream(APPLICATION_RESOURCE)) {
                System.setProperty(RESOURCE_STREAM_PROPERTY,
                        new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
            }
            try (InputStream inputStream = contextClassLoader.getResourceAsStream(SERVICE_RESOURCE)) {
                System.setProperty(SERVICE_PROPERTY, new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
            }
            Enumeration<URL> applicationResources = contextClassLoader.getResources(APPLICATION_RESOURCE);
            int applicationResourceCount = 0;
            while (applicationResources.hasMoreElements()) {
                applicationResources.nextElement();
                applicationResourceCount++;
            }
            System.setProperty(RESOURCE_COUNT_PROPERTY, Integer.toString(applicationResourceCount));
            Enumeration<URL> configurationFiles = contextClassLoader.getResources(APPLICATION_PROPERTIES);
            int count = 0;
            while (configurationFiles.hasMoreElements()) {
                configurationFiles.nextElement();
                count++;
            }
            System.setProperty(CONFIG_COUNT_PROPERTY, Integer.toString(count));
        }
    }
}
