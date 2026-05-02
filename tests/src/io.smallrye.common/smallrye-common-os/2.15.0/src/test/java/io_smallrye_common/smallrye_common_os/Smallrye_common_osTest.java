/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_common.smallrye_common_os;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.ProcessBuilder.Redirect;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import io.smallrye.common.os.OS;
import io.smallrye.common.os.Process;
import io.smallrye.common.os.ProcessInfo;
import io.smallrye.common.os.ProcessRedirect;

public class Smallrye_common_osTest {
    @Test
    void currentOsMatchesTheRunningJvmOsName() {
        OS currentOs = OS.current();

        assertThat(currentOs).isEqualTo(expectedCurrentOs());
        assertThat(currentOs.isCurrent()).isTrue();
        assertThat(Arrays.stream(OS.values()).filter(OS::isCurrent))
                .containsExactly(currentOs);
    }

    @Test
    void osEnumExposesStablePublicConstants() {
        assertThat(OS.values())
                .containsExactly(OS.AIX, OS.LINUX, OS.MAC, OS.SOLARIS, OS.WINDOWS, OS.Z, OS.OTHER);
        assertThat(OS.valueOf("LINUX")).isSameAs(OS.LINUX);
        assertThat(OS.valueOf("WINDOWS")).isSameAs(OS.WINDOWS);
        assertThat(OS.valueOf("MAC")).isSameAs(OS.MAC);
        assertThat(OS.valueOf("Z")).isSameAs(OS.Z);
    }

    @Test
    void currentProcessInfoIsConsistentWithProcessConvenienceMethodsAndJdkProcessHandle() {
        ProcessInfo currentProcess = Process.getCurrentProcess();

        assertThat(currentProcess.getId()).isEqualTo(ProcessHandle.current().pid());
        assertThat(Process.getProcessId()).isEqualTo(currentProcess.getId());
        assertThat(Process.getProcessName()).isEqualTo(currentProcess.getCommand());
        assertThat(currentProcess.getCommand()).isNotBlank();
    }

    @Test
    void allProcessesContainsTheCurrentProcessAndValidProcessIds() {
        List<ProcessInfo> processes = Process.getAllProcesses();
        long currentProcessId = Process.getProcessId();

        assertThat(processes).isNotEmpty();
        assertThat(processes)
                .extracting(ProcessInfo::getId)
                .allSatisfy(processId -> assertThat(processId).isPositive())
                .contains(currentProcessId);

        Set<Long> processIds = processes.stream()
                .map(ProcessInfo::getId)
                .collect(Collectors.toSet());
        assertThat(processIds).hasSameSizeAs(processes);
    }

    @Test
    void allProcessesReportsCommandFromJdkProcessHandleInfo() {
        ProcessHandle currentHandle = ProcessHandle.current();
        ProcessInfo currentProcessFromList = Process.getAllProcesses().stream()
                .filter(process -> process.getId() == currentHandle.pid())
                .findFirst()
                .orElseThrow();

        assertThat(currentProcessFromList.getCommand()).isEqualTo(currentHandle.info().command().orElse(null));
    }

    @Test
    void processInfoConstructorStoresIdAndCommandUnchanged() {
        ProcessInfo processInfo = new ProcessInfo(1234L, "command with arguments");

        assertThat(processInfo.getId()).isEqualTo(1234L);
        assertThat(processInfo.getCommand()).isEqualTo("command with arguments");
    }

    @Test
    void discardRedirectReturnsTheJdkDiscardRedirect() {
        Redirect discardRedirect = ProcessRedirect.discard();

        assertThat(discardRedirect).isSameAs(Redirect.DISCARD);
        assertThat(discardRedirect.type()).isEqualTo(Redirect.Type.WRITE);
    }

    @Test
    void discardRedirectCanBeUsedToRunProcessWithoutCapturingOutput() throws Exception {
        ProcessBuilder processBuilder = commandThatWritesToStandardOutput()
                .redirectOutput(ProcessRedirect.discard())
                .redirectError(ProcessRedirect.discard());

        java.lang.Process process = processBuilder.start();

        assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
        assertThat(process.getInputStream().read()).isEqualTo(-1);
    }

    private static ProcessBuilder commandThatWritesToStandardOutput() {
        if (OS.current() == OS.WINDOWS) {
            return new ProcessBuilder("cmd", "/c", "echo discarded-output");
        }
        return new ProcessBuilder("sh", "-c", "printf discarded-output");
    }

    private static OS expectedCurrentOs() {
        String osName = System.getProperty("os.name", "unknown").toLowerCase(Locale.ENGLISH);
        if (osName.contains("linux")) {
            return OS.LINUX;
        }
        if (osName.contains("windows")) {
            return OS.WINDOWS;
        }
        if (osName.contains("mac") || osName.contains("darwin")) {
            return OS.MAC;
        }
        if (osName.contains("sunos") || osName.contains("solaris")) {
            return OS.SOLARIS;
        }
        if (osName.contains("aix")) {
            return OS.AIX;
        }
        if (osName.contains("z/os")) {
            return OS.Z;
        }
        return OS.OTHER;
    }
}
