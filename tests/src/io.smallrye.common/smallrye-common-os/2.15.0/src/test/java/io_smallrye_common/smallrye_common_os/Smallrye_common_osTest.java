/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_common.smallrye_common_os;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import io.smallrye.common.os.Linux;
import io.smallrye.common.os.OS;
import io.smallrye.common.os.Process;

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
    void processNameMatchesConfiguredOverrideOrJdkProcessHandle() {
        String configuredProcessName = System.getProperty("jboss.process.name");
        String expectedProcessName = configuredProcessName != null
                ? configuredProcessName
                : ProcessHandle.current().info().command().orElse("<unknown>");

        assertThat(Process.getProcessName()).isEqualTo(expectedProcessName);
        assertThat(Process.getProcessName()).isNotBlank();
    }

    @Test
    void linuxWslDetectionIsConsistentWithCurrentOs() {
        boolean windowsSubsystemForLinux = Linux.isWSL();
        boolean windowsSubsystemForLinuxVersionTwo = Linux.isWSLv2();

        if (OS.current() != OS.LINUX) {
            assertThat(windowsSubsystemForLinux).isFalse();
            assertThat(windowsSubsystemForLinuxVersionTwo).isFalse();
        }
        if (windowsSubsystemForLinuxVersionTwo) {
            assertThat(windowsSubsystemForLinux).isTrue();
        }
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
