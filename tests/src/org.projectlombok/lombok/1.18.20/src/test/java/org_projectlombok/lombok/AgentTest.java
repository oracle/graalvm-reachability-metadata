/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_projectlombok.lombok;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import lombok.Lombok;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AgentTest {
    private static final Duration PROCESS_TIMEOUT = Duration.ofSeconds(30);

    @Test
    void javaAgentEntryPointLaunchesShadowAgentLauncher() throws Exception {
        Path lombokJar = locateLombokJar();

        try {
            loadAgentIntoCurrentVirtualMachine(lombokJar);
        } catch (AttachNotSupportedException | IOException | UnsupportedOperationException exception) {
            assertForkedJavaProcessStartsWithAgent(lombokJar);
        } catch (Error error) {
            if (!isUnsupportedAttachError(error)) {
                throw error;
            }
            assertForkedJavaProcessStartsWithAgent(lombokJar);
        }
    }

    private static void loadAgentIntoCurrentVirtualMachine(Path lombokJar) throws Exception {
        System.setProperty("jdk.attach.allowAttachSelf", "true");
        VirtualMachine virtualMachine = VirtualMachine.attach(currentProcessId());
        try {
            virtualMachine.loadAgent(lombokJar.toString(), "");
        } finally {
            virtualMachine.detach();
        }
    }

    private static void assertForkedJavaProcessStartsWithAgent(Path lombokJar) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(javaExecutable().toString());
        command.add("-javaagent:" + lombokJar);
        command.add("-version");

        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(PROCESS_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(finished)
                .as("Forked JVM output:%n%s", output)
                .isTrue();
        assertThat(process.exitValue())
                .as("Forked JVM output:%n%s", output)
                .isZero();
    }

    private static boolean isUnsupportedAttachError(Error error) {
        if (error instanceof LinkageError) {
            return true;
        }
        Throwable cause = error;
        while (cause != null) {
            if (cause instanceof Error causeError && NativeImageSupport.isUnsupportedFeatureError(causeError)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private static Path locateLombokJar() throws Exception {
        Optional<Path> classPathEntry = findLombokJarOnClassPath();
        if (classPathEntry.isPresent()) {
            return classPathEntry.get();
        }

        CodeSource codeSource = Lombok.class.getProtectionDomain().getCodeSource();
        assertThat(codeSource).as("Lombok code source").isNotNull();
        Path location = Path.of(codeSource.getLocation().toURI());
        assertThat(location.getFileName().toString()).startsWith("lombok-").endsWith(".jar");
        return location;
    }

    private static Optional<Path> findLombokJarOnClassPath() {
        String classPath = System.getProperty("java.class.path", "");
        for (String entry : classPath.split(Pattern.quote(File.pathSeparator))) {
            if (entry.isBlank()) {
                continue;
            }
            Path path = Path.of(entry);
            Path fileName = path.getFileName();
            if (fileName == null) {
                continue;
            }
            String name = fileName.toString();
            if (name.startsWith("lombok-") && name.endsWith(".jar") && Files.isRegularFile(path)) {
                return Optional.of(path);
            }
        }
        return Optional.empty();
    }

    private static Path javaExecutable() {
        Path fromJavaHome = javaExecutable(System.getProperty("java.home"));
        if (Files.isExecutable(fromJavaHome)) {
            return fromJavaHome;
        }
        return javaExecutable(System.getenv("JAVA_HOME"));
    }

    private static Path javaExecutable(String javaHome) {
        if (javaHome == null || javaHome.isBlank()) {
            return Path.of("java");
        }
        return Path.of(javaHome, "bin", javaExecutableName());
    }

    private static String javaExecutableName() {
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return "java.exe";
        }
        return "java";
    }

    private static String currentProcessId() {
        String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
        int separator = runtimeName.indexOf('@');
        if (separator > 0) {
            return runtimeName.substring(0, separator);
        }
        return Long.toString(ProcessHandle.current().pid());
    }
}
