/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import com.sun.tools.attach.VirtualMachine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LoggerFactoryTest {

    @Test
    void initializesWithNopFallbackWhenLoadedFromBootstrapSearch() throws Exception {
        appendSlf4jApiJarToBootstrapSearch();

        assertThat(LoggerFactory.getILoggerFactory().getClass().getName())
                .isEqualTo("org.slf4j.helpers.NOPLoggerFactory");
        assertThat(LoggerFactory.getLogger(LoggerFactoryTest.class).isInfoEnabled()).isFalse();
    }

    private static void appendSlf4jApiJarToBootstrapSearch() throws Exception {
        if (LoggerFactoryBootstrapAgent.isSlf4jAvailableFromBootstrap()) {
            return;
        }

        Path slf4jApiJar = findSlf4jApiJar();
        Path agentJar = createAgentJar();
        String currentPid = Long.toString(ProcessHandle.current().pid());
        String javaExecutable = findJavaExecutable().toString();
        String classpath = System.getProperty("java.class.path");

        Process process = new ProcessBuilder(
                javaExecutable,
                "-cp",
                classpath,
                LoggerFactoryAttachHelper.class.getName(),
                currentPid,
                agentJar.toString(),
                slf4jApiJar.toString()
        ).inheritIO().start();

        assertThat(process.waitFor()).isZero();
        assertThat(LoggerFactoryBootstrapAgent.isSlf4jAvailableFromBootstrap()).isTrue();
    }

    private static Path findSlf4jApiJar() {
        String[] classpathEntries = System.getProperty("java.class.path").split(System.getProperty("path.separator"));
        for (String classpathEntry : classpathEntries) {
            Path path = Path.of(classpathEntry);
            if (Files.isRegularFile(path) && path.getFileName().toString().matches("slf4j-api-1\\.6\\.1(?:-.*)?\\.jar")) {
                return path;
            }
        }
        throw new IllegalStateException("Could not find slf4j-api jar on the test classpath");
    }

    private static Path createAgentJar() throws IOException {
        Path agentJar = Files.createTempFile("logger-factory-bootstrap-agent", ".jar");
        agentJar.toFile().deleteOnExit();

        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.putValue("Agent-Class", LoggerFactoryBootstrapAgent.class.getName());

        try (JarOutputStream outputStream = new JarOutputStream(Files.newOutputStream(agentJar), manifest)) {
            writeClassEntry(outputStream, LoggerFactoryBootstrapAgent.class);
        }

        return agentJar;
    }

    private static void writeClassEntry(JarOutputStream outputStream, Class<?> type) throws IOException {
        String resourceName = type.getName().replace('.', '/') + ".class";
        outputStream.putNextEntry(new java.util.jar.JarEntry(resourceName));
        try (InputStream inputStream = type.getClassLoader().getResourceAsStream(resourceName)) {
            if (inputStream == null) {
                throw new IllegalStateException("Could not read class bytes for " + type.getName());
            }
            inputStream.transferTo(outputStream);
        }
        outputStream.closeEntry();
    }

    private static Path findJavaExecutable() {
        Path javaHome = Path.of(System.getProperty("java.home"));
        Path java = javaHome.resolve(Path.of("bin", "java"));
        if (Files.isRegularFile(java)) {
            return java;
        }
        Path windowsJava = javaHome.resolve(Path.of("bin", "java.exe"));
        if (Files.isRegularFile(windowsJava)) {
            return windowsJava;
        }
        throw new IllegalStateException("Could not locate the java executable for the current JDK");
    }
}

class LoggerFactoryBootstrapAgent {

    private static volatile boolean slf4jAvailableFromBootstrap;

    public static void agentmain(String arguments, Instrumentation instrumentation) throws Exception {
        Path slf4jApiJar = Path.of(arguments);
        instrumentation.appendToBootstrapClassLoaderSearch(new JarFile(slf4jApiJar.toFile()));
        slf4jAvailableFromBootstrap = true;
    }

    static boolean isSlf4jAvailableFromBootstrap() {
        return slf4jAvailableFromBootstrap;
    }
}

class LoggerFactoryAttachHelper {

    public static void main(String[] arguments) throws Exception {
        String pid = arguments[0];
        String agentJar = arguments[1];
        String slf4jApiJar = arguments[2];

        VirtualMachine virtualMachine = VirtualMachine.attach(pid);
        try {
            virtualMachine.loadAgent(agentJar, slf4jApiJar);
        } finally {
            virtualMachine.detach();
        }
    }
}
