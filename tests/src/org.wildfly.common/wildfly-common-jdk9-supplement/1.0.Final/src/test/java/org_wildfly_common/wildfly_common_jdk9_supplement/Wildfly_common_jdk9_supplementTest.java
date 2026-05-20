/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_wildfly_common.wildfly_common_jdk9_supplement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.wildfly.common.cpu.ProcessorInfo.availableProcessors;
import static org.wildfly.common.os.Process.getProcessId;
import static org.wildfly.common.os.Process.getProcessName;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(OrderAnnotation.class)
public class Wildfly_common_jdk9_supplementTest {
    private static final String JBOSS_PROCESS_NAME_PROPERTY = "jboss.process.name";

    @Test
    @Order(1)
    void processNameUsesConfiguredJbossProcessName() {
        String configuredProcessName = "wildfly-common-jdk9-supplement-test";
        String originalProcessName = System.getProperty(JBOSS_PROCESS_NAME_PROPERTY);
        System.setProperty(JBOSS_PROCESS_NAME_PROPERTY, configuredProcessName);
        try {
            assertThat(getProcessName()).isEqualTo(configuredProcessName);
        } finally {
            if (originalProcessName == null) {
                System.clearProperty(JBOSS_PROCESS_NAME_PROPERTY);
            } else {
                System.setProperty(JBOSS_PROCESS_NAME_PROPERTY, originalProcessName);
            }
        }
    }

    @Test
    @Order(2)
    void processorInfoReportsTheRuntimeProcessorCount() {
        int runtimeProcessors = Runtime.getRuntime().availableProcessors();
        int wildflyProcessors = availableProcessors();

        assertThat(wildflyProcessors).isEqualTo(runtimeProcessors);
        assertThat(wildflyProcessors).isPositive();
    }

    @Test
    @Order(3)
    void processIdMatchesTheCurrentJavaProcess() {
        long processId = getProcessId();

        assertThat(processId).isEqualTo(ProcessHandle.current().pid());
        assertThat(processId).isPositive();
        assertThat(getProcessId()).isEqualTo(processId);
    }

    @Test
    @Order(4)
    void processNameIsResolvedAndStable() {
        String processName = getProcessName();

        assertThat(processName).isNotBlank();
        assertThat(getProcessName()).isSameAs(processName);
    }

    @Test
    @Order(5)
    void processNameCanBeDerivedFromJavaCommandClassName() throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(List.of(
                javaCommand(),
                "-cp",
                processProbeClasspath(),
                ProcessNameProbe.class.getName(),
                "resolve-simple-name"))
                .redirectErrorStream(true);
        processBuilder.environment().remove("JAVA_TOOL_OPTIONS");
        processBuilder.environment().remove("JDK_JAVA_OPTIONS");
        processBuilder.environment().remove("_JAVA_OPTIONS");
        Process process = processBuilder.start();

        boolean finished = process.waitFor(10, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
        }
        String output;
        try (InputStream processOutput = process.getInputStream()) {
            output = new String(processOutput.readAllBytes(), StandardCharsets.UTF_8).trim();
        }

        assertThat(finished).as(output).isTrue();
        assertThat(process.exitValue()).as(output).isZero();
        assertThat(output).isEqualTo("ProcessNameProbe");
    }

    private static String javaCommand() {
        String javaHome = System.getProperty("java.home");
        if (javaHome == null || javaHome.isBlank()) {
            javaHome = System.getenv("JAVA_HOME");
        }
        if (javaHome == null || javaHome.isBlank()) {
            return "java";
        }

        Path java = Path.of(javaHome, "bin", javaExecutableName());
        if (Files.isExecutable(java)) {
            return java.toString();
        }
        return "java";
    }

    private static String javaExecutableName() {
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return "java.exe";
        }
        return "java";
    }

    private static String processProbeClasspath() throws Exception {
        String javaClasspath = System.getProperty("java.class.path");
        if (javaClasspath != null && !javaClasspath.isBlank()) {
            return javaClasspath;
        }

        return String.join(System.getProperty("path.separator"),
                Path.of("build", "classes", "java", "test").toString(),
                wildflyCommonClassesDirectory().toString());
    }

    private static Path wildflyCommonClassesDirectory() throws Exception {
        Path effectiveClasses = Path.of("build", "jacoco", "effective");
        try (Stream<Path> paths = Files.walk(effectiveClasses)) {
            return paths.filter(Files::isDirectory)
                    .filter(path -> Files.exists(path.resolve("org/wildfly/common/os/Process.class")))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("WildFly Common classes directory was not found"));
        }
    }
}

final class ProcessNameProbe {
    private ProcessNameProbe() {
    }

    public static void main(String[] args) {
        System.out.println(getProcessName());
    }
}
