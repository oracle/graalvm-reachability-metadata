/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_wildfly_common.wildfly_common_jdk9_supplement;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.wildfly.common.cpu.ProcessorInfo;
import org.wildfly.common.os.Process;

public class WildflyCommonJdk9SupplementTest {
    private static final String PRINT_PROCESS_NAME_ARGUMENT = "print-process-name";
    private static final String PROCESS_NAME_PROPERTY = "jboss.process.name";

    @Test
    void availableProcessorCountMatchesRuntime() {
        int availableProcessors = ProcessorInfo.availableProcessors();

        assertThat(availableProcessors).isEqualTo(Runtime.getRuntime().availableProcessors());
        assertThat(availableProcessors).isPositive();
    }

    @Test
    void processIdMatchesCurrentProcessHandle() {
        long processId = Process.getProcessId();

        assertThat(processId).isEqualTo(ProcessHandle.current().pid());
        assertThat(processId).isPositive();
    }

    @Test
    void processNameMatchesWildFlyCommonDerivationRules() {
        String processName = Process.getProcessName();

        assertThat(processName).isEqualTo(expectedProcessName());
        assertThat(processName).isNotNull();
    }

    @Test
    void processInformationIsStableAcrossCalls() {
        long processId = Process.getProcessId();
        String processName = Process.getProcessName();

        assertThat(Process.getProcessId()).isEqualTo(processId);
        assertThat(Process.getProcessName()).isSameAs(processName);
    }

    @Test
    void configuredProcessNameOverridesCommandDerivedName() throws Exception {
        String configuredProcessName = "wildfly-common-configured-process";
        java.lang.Process process = new ProcessBuilder(processNamePrinterCommand(configuredProcessName)).start();

        boolean finished = process.waitFor(10, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        String errorOutput = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();

        assertThat(finished).as("child JVM completed").isTrue();
        assertThat(process.exitValue()).as(errorOutput).isEqualTo(0);
        assertThat(output).isEqualTo(configuredProcessName);
    }

    public static void main(String[] args) {
        if (args.length == 1 && PRINT_PROCESS_NAME_ARGUMENT.equals(args[0])) {
            System.out.print(Process.getProcessName());
            return;
        }
        throw new IllegalArgumentException("Unsupported arguments");
    }

    private static List<String> processNamePrinterCommand(String processName) {
        String classPath = firstNonBlank(System.getProperty("java.class.path"), System.getenv("CLASSPATH"));
        assertThat(classPath).isNotBlank();

        List<String> command = new ArrayList<>();
        command.add(javaExecutable().toString());
        command.add("-D" + PROCESS_NAME_PROPERTY + "=" + processName);
        command.add("-cp");
        command.add(classPath);
        command.add(WildflyCommonJdk9SupplementTest.class.getName());
        command.add(PRINT_PROCESS_NAME_ARGUMENT);
        return command;
    }

    private static Path javaExecutable() {
        List<String> javaHomes = List.of(
                System.getProperty("java.home", ""),
                System.getenv("JAVA_HOME") == null ? "" : System.getenv("JAVA_HOME"),
                System.getenv("GRAALVM_HOME") == null ? "" : System.getenv("GRAALVM_HOME"));
        for (String javaHome : javaHomes) {
            if (!javaHome.isBlank()) {
                Path executable = Path.of(javaHome, "bin", isWindows() ? "java.exe" : "java");
                if (Files.isRegularFile(executable)) {
                    return executable;
                }
            }
        }

        Path executable = Path.of(
                firstNonBlank(javaHomes.toArray(String[]::new)),
                "bin",
                isWindows() ? "java.exe" : "java");
        assertThat(executable).exists();
        return executable;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return "";
    }

    private static String expectedProcessName() {
        String processName = System.getProperty("jboss.process.name");
        if (processName == null) {
            processName = processNameFromJavaCommand();
        }
        if (processName == null) {
            processName = ProcessHandle.current().info().command().orElse(null);
        }
        if (processName == null) {
            return "<unknown>";
        }
        return processName;
    }

    private static String processNameFromJavaCommand() {
        String classPath = System.getProperty("java.class.path");
        String javaCommand = System.getProperty("sun.java.command");
        if (javaCommand == null) {
            return null;
        }
        if (classPath != null && javaCommand.startsWith(classPath)) {
            return fileName(classPath);
        }
        return commandName(javaCommand);
    }

    private static String fileName(String path) {
        int separatorIndex = path.lastIndexOf(File.separatorChar);
        if (separatorIndex > 0) {
            return path.substring(separatorIndex + 1);
        }
        return path;
    }

    private static String commandName(String javaCommand) {
        int argumentSeparatorIndex = javaCommand.indexOf(' ');
        String firstCommandPart = argumentSeparatorIndex > 0
                ? javaCommand.substring(0, argumentSeparatorIndex)
                : javaCommand;
        int extensionSeparatorIndex = firstCommandPart.lastIndexOf('.', argumentSeparatorIndex);
        if (extensionSeparatorIndex <= 0) {
            return firstCommandPart;
        }

        String extension = firstCommandPart.substring(extensionSeparatorIndex + 1);
        if ("jar".equalsIgnoreCase(extension) || "ȷar".equalsIgnoreCase(extension)) {
            return jarFileName(firstCommandPart, extensionSeparatorIndex);
        }
        return extension;
    }

    private static String jarFileName(String commandName, int extensionSeparatorIndex) {
        int previousExtensionSeparatorIndex = commandName.lastIndexOf('.', extensionSeparatorIndex - 1);
        int pathSeparatorIndex = commandName.lastIndexOf(File.separatorChar);
        int fileNameSeparatorIndex;
        if (previousExtensionSeparatorIndex == -1) {
            fileNameSeparatorIndex = pathSeparatorIndex;
        } else if (pathSeparatorIndex == -1) {
            fileNameSeparatorIndex = previousExtensionSeparatorIndex;
        } else {
            fileNameSeparatorIndex = Math.max(pathSeparatorIndex, previousExtensionSeparatorIndex);
        }
        if (fileNameSeparatorIndex > 0) {
            return commandName.substring(fileNameSeparatorIndex + 1);
        }
        return commandName;
    }
}
