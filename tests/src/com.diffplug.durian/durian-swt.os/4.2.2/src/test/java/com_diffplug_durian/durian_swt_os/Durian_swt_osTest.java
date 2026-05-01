/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_durian.durian_swt_os;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.diffplug.common.swt.os.Arch;
import com.diffplug.common.swt.os.OS;
import com.diffplug.common.swt.os.SwtPlatform;
import com.diffplug.common.swt.os.WS;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Durian_swt_osTest {
    private static final Map<OS, Arch> EXPECTED_ARCH = Map.ofEntries(
            entry(OS.WIN_x64, Arch.x64),
            entry(OS.WIN_x86, Arch.x86),
            entry(OS.LINUX_x64, Arch.x64),
            entry(OS.LINUX_x86, Arch.x86),
            entry(OS.MAC_x64, Arch.x64),
            entry(OS.MAC_silicon, Arch.arm64),
            entry(OS.WIN_unknown, Arch.unknown),
            entry(OS.LINUX_unknown, Arch.unknown),
            entry(OS.MAC_unknown, Arch.unknown));

    private static final Map<OS, String> EXPECTED_PLATFORM = Map.ofEntries(
            entry(OS.WIN_x64, "win32.win32.x86_64"),
            entry(OS.WIN_x86, "win32.win32.x86"),
            entry(OS.LINUX_x64, "gtk.linux.x86_64"),
            entry(OS.LINUX_x86, "gtk.linux.x86"),
            entry(OS.MAC_x64, "cocoa.macosx.x86_64"),
            entry(OS.MAC_silicon, "cocoa.macosx.aarch64"),
            entry(OS.WIN_unknown, "win32.win32.unknown"),
            entry(OS.LINUX_unknown, "gtk.linux.unknown"),
            entry(OS.MAC_unknown, "cocoa.macosx.unknown"));

    @Test
    @Order(1)
    void detectPlatformUsesInjectedSystemAndEnvironmentAccessors() {
        OS.detectPlatform(
                key -> switch (key) {
                    case "os.name" -> "Linux";
                    case "os.arch" -> "x86_64";
                    case "sun.arch.data.model" -> "64";
                    default -> null;
                },
                key -> null);

        assertThat(OS.getNative()).isEqualTo(OS.LINUX_x64);
        assertThat(OS.getRunning()).isEqualTo(OS.LINUX_x64);
    }

    @Test
    void archSelectorsReturnTheMatchingBranch() {
        assertThat(Arch.x86.x86x64("32-bit", "64-bit")).isEqualTo("32-bit");
        assertThat(Arch.x64.x86x64("32-bit", "64-bit")).isEqualTo("64-bit");

        assertThat(Arch.x64.x64arm64("x64", "arm64")).isEqualTo("x64");
        assertThat(Arch.arm64.x64arm64("x64", "arm64")).isEqualTo("arm64");

        assertThat(Arch.x86.x86x64arm64("x86", "x64", "arm64")).isEqualTo("x86");
        assertThat(Arch.x64.x86x64arm64("x86", "x64", "arm64")).isEqualTo("x64");
        assertThat(Arch.arm64.x86x64arm64("x86", "x64", "arm64")).isEqualTo("arm64");

        assertThat(Arch.x86.x86x64arm64unknown("x86", "x64", "arm64", "unknown")).isEqualTo("x86");
        assertThat(Arch.x64.x86x64arm64unknown("x86", "x64", "arm64", "unknown")).isEqualTo("x64");
        assertThat(Arch.arm64.x86x64arm64unknown("x86", "x64", "arm64", "unknown")).isEqualTo("arm64");
        assertThat(Arch.unknown.x86x64arm64unknown("x86", "x64", "arm64", "unknown")).isEqualTo("unknown");
    }

    @Test
    void archSelectorsRejectUnsupportedBranches() {
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> Arch.arm64.x86x64("x86", "x64"))
                .withMessage("Arch 'arm64' is unsupported.");
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> Arch.unknown.x64arm64("x64", "arm64"))
                .withMessage("Arch 'unknown' is unsupported.");
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> Arch.unknown.x86x64arm64("x86", "x64", "arm64"))
                .withMessage("Arch 'unknown' is unsupported.");

        assertThat(Arch.unsupportedException(Arch.x86))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Arch 'x86' is unsupported.");
    }

    @Test
    void windowSystemPredicatesAndSelectorIdentifyEachWindowSystem() {
        assertThat(WS.WIN.isWin()).isTrue();
        assertThat(WS.WIN.isCocoa()).isFalse();
        assertThat(WS.WIN.isGTK()).isFalse();
        assertThat(WS.WIN.winCocoaGtk("win", "cocoa", "gtk")).isEqualTo("win");

        assertThat(WS.COCOA.isWin()).isFalse();
        assertThat(WS.COCOA.isCocoa()).isTrue();
        assertThat(WS.COCOA.isGTK()).isFalse();
        assertThat(WS.COCOA.winCocoaGtk("win", "cocoa", "gtk")).isEqualTo("cocoa");

        assertThat(WS.GTK.isWin()).isFalse();
        assertThat(WS.GTK.isCocoa()).isFalse();
        assertThat(WS.GTK.isGTK()).isTrue();
        assertThat(WS.GTK.winCocoaGtk("win", "cocoa", "gtk")).isEqualTo("gtk");

        assertThat(WS.unsupportedException(WS.GTK))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Window system 'GTK' is unsupported.");
    }

    @Test
    void osPredicatesSelectorsAndStringAccessorsAreConsistent() {
        assertOs(OS.WIN_x64, true, false, false, "win");
        assertOs(OS.WIN_x86, true, false, false, "win");
        assertOs(OS.WIN_unknown, true, false, false, "win");
        assertOs(OS.LINUX_x64, false, true, false, "linux");
        assertOs(OS.LINUX_x86, false, true, false, "linux");
        assertOs(OS.LINUX_unknown, false, true, false, "linux");
        assertOs(OS.MAC_x64, false, false, true, "mac");
        assertOs(OS.MAC_silicon, false, false, true, "mac");
        assertOs(OS.MAC_unknown, false, false, true, "mac");

        for (OS os : OS.values()) {
            assertThat(os.getArch()).isEqualTo(EXPECTED_ARCH.get(os));
            assertThat(os.arch()).isEqualTo(archString(EXPECTED_ARCH.get(os)));
            assertThat(os.os()).isNotBlank();
            assertThat(os.osDotArch()).isEqualTo(os.os() + "." + os.arch());
            assertThat(os.toSwt()).isEqualTo(EXPECTED_PLATFORM.get(os));
        }

        assertThat(OS.unsupportedException(OS.LINUX_x64))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Operating system 'LINUX_x64' is not supported.");
    }

    @Test
    void swtPlatformsRoundTripAllKnownOperatingSystems() {
        List<SwtPlatform> expectedPlatforms = Arrays.stream(OS.values())
                .map(SwtPlatform::fromOS)
                .toList();

        for (OS os : OS.values()) {
            SwtPlatform platform = SwtPlatform.fromOS(os);
            String[] parts = EXPECTED_PLATFORM.get(os).split("\\.", -1);

            assertThat(platform.getWs()).isEqualTo(parts[0]);
            assertThat(platform.getOs()).isEqualTo(parts[1]);
            assertThat(platform.getArch()).isEqualTo(parts[2]);
            assertThat(platform.toString()).isEqualTo(EXPECTED_PLATFORM.get(os));
            assertThat(platform.toOS()).isEqualTo(os);
            assertThat(platform.platformFilter()).isEqualTo(
                    "(& (osgi.ws=" + parts[0] + ") (osgi.os=" + parts[1] + ") (osgi.arch=" + parts[2] + ") )");
            assertThat(platform.platformProperties())
                    .containsExactly(
                            entry("osgi.ws", parts[0]),
                            entry("osgi.os", parts[1]),
                            entry("osgi.arch", parts[2]));
        }

        assertThat(SwtPlatform.getAll()).containsExactlyElementsOf(expectedPlatforms);
    }

    @Test
    void swtPlatformParsingEqualityAndWuffMappingsWorkForSupportedPlatforms() {
        SwtPlatform parsed = SwtPlatform.parseWsOsArch("win32.win32.x86_64");
        SwtPlatform same = SwtPlatform.parseWsOsArch("win32.win32.x86_64");
        SwtPlatform different = SwtPlatform.parseWsOsArch("gtk.linux.x86_64");

        assertThat(parsed.getWs()).isEqualTo("win32");
        assertThat(parsed.getOs()).isEqualTo("win32");
        assertThat(parsed.getArch()).isEqualTo("x86_64");
        assertThat(parsed).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(parsed).isNotEqualTo(different).isNotEqualTo("win32.win32.x86_64");

        Map<String, String> expectedWuffStrings = Map.of(
                "cocoa.macosx.x86_64", "macosx-x86_64",
                "gtk.linux.x86", "linux-x86_32",
                "gtk.linux.x86_64", "linux-x86_64",
                "win32.win32.x86", "windows-x86_32",
                "win32.win32.x86_64", "windows-x86_64");
        for (Map.Entry<String, String> entry : expectedWuffStrings.entrySet()) {
            assertThat(SwtPlatform.parseWsOsArch(entry.getKey()).getWuffString()).isEqualTo(entry.getValue());
        }
    }

    @Test
    void swtPlatformRejectsInvalidOrUnknownPlatforms() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> SwtPlatform.parseWsOsArch("gtk.linux"))
                .withMessage("gtk.linux should have the form 'ws.os.arch'.");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> SwtPlatform.parseWsOsArch("gtk.linux.x86.extra"))
                .withMessage("gtk.linux.x86.extra should have the form 'ws.os.arch'.");

        SwtPlatform unknownPlatform = SwtPlatform.parseWsOsArch("custom.os.arch");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(unknownPlatform::toOS)
                .withMessage("No known OS matches this platform: custom.os.arch");
        assertThatThrownBy(unknownPlatform::getWuffString).isInstanceOf(NullPointerException.class);
    }

    @Test
    void commandLineEntryPointPrintsDetectedNativeAndRunningOperatingSystems() {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            OS.main(new String[0]);
        } finally {
            System.setOut(originalOut);
        }

        String[] lines = output.toString(StandardCharsets.UTF_8).strip().split("\\R");
        assertThat(lines).containsExactly("native=" + OS.getNative(), "running=" + OS.getRunning());
    }

    @Test
    void nativeAndRunningAccessorsExposeConsistentPlatformsAndArchitectures() {
        OS nativeOs = OS.getNative();
        OS runningOs = OS.getRunning();
        WS runningWs = WS.getRunning();

        assertThat(nativeOs).isIn((Object[]) OS.values());
        assertThat(runningOs).isIn((Object[]) OS.values());
        assertThat(Arch.getNative()).isEqualTo(nativeOs.getArch());
        assertThat(Arch.getRunning()).isEqualTo(runningOs.getArch());
        assertThat(SwtPlatform.getNative()).isEqualTo(SwtPlatform.fromOS(nativeOs));
        assertThat(SwtPlatform.getRunning()).isEqualTo(SwtPlatform.fromOS(runningOs));
        assertThat(runningWs).isEqualTo(runningOs.winMacLinux(WS.WIN, WS.COCOA, WS.GTK));
    }

    private static void assertOs(OS os, boolean windows, boolean linux, boolean mac, String expectedBranch) {
        assertThat(os.isWindows()).isEqualTo(windows);
        assertThat(os.isLinux()).isEqualTo(linux);
        assertThat(os.isMac()).isEqualTo(mac);
        assertThat(os.isMacOrLinux()).isEqualTo(mac || linux);
        assertThat(os.winMacLinux("win", "mac", "linux")).isEqualTo(expectedBranch);
    }

    private static String archString(Arch arch) {
        return arch.x86x64arm64unknown("x86", "x86_64", "aarch64", "unknown");
    }
}
