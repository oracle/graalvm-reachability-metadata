/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.logging.JDKLogImpl;
import org.jgroups.logging.Log;
import org.jgroups.logging.LogFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class ALogFactoryTest {
    private static final String LOG_CLASS_PROPERTY = "jgroups.log_class";
    private static final String JAVA_EXECUTABLE_NAME = System.getProperty("os.name").toLowerCase().contains("win")
        ? "java.exe"
        : "java";

    @BeforeAll
    public static void configureLogFactory() {
        System.setProperty(LOG_CLASS_PROPERTY, JDKLogImpl.class.getName());
    }

    @Test
    void createsConfiguredLoggerUsingPublicFactory() {
        Log log = LogFactory.getLog(ALogFactoryTest.class);

        assertThat(log).isInstanceOf(JDKLogImpl.class);
        assertThat(LogFactory.loggerType()).isEqualTo("JDKLogImpl");

        log.info("configured JGroups logger is usable");
    }

    @Test
    void probesAvailableLoggerImplementationsInFreshJvm() throws Exception {
        List<String> command = new ArrayList<>();
        command.add(javaExecutable().toString());
        jacocoAgentArgument().ifPresent(command::add);
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(LogFactoryAvailabilityExercise.class.getName());

        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(process.waitFor()).as(output).isZero();
        assertThat(output).contains("loggerType=");
    }

    private static Path javaExecutable() {
        return Path.of(System.getProperty("java.home"), "bin", JAVA_EXECUTABLE_NAME);
    }

    private static Optional<String> jacocoAgentArgument() {
        return ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
            .filter(argument -> argument.startsWith("-javaagent:"))
            .filter(argument -> argument.contains("jacoco"))
            .findFirst();
    }
}

final class LogFactoryAvailabilityExercise {
    private LogFactoryAvailabilityExercise() {
    }

    public static void main(String[] args) {
        Log log = LogFactory.getLog(LogFactoryAvailabilityExercise.class);

        log.info("default JGroups logger is usable");
        System.out.println("loggerType=" + LogFactory.loggerType());
    }
}
