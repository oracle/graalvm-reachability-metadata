/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_wildfly_common.wildfly_common_jdk9_supplement;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.junit.jupiter.api.Test;
import org.wildfly.common.cpu.ProcessorInfo;
import org.wildfly.common.os.Process;

public class WildflyCommonJdk9SupplementTest {
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
