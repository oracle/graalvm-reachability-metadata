/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ajalt_mordant.mordant_jvm_jna_jvm

import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalInterface
import com.github.ajalt.mordant.terminal.TerminalInterfaceProvider
import com.github.ajalt.mordant.terminal.terminalinterface.jna.TerminalInterfaceProviderJna
import java.util.ServiceLoader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.junit.jupiter.api.parallel.Resources

@ResourceLock(Resources.SYSTEM_PROPERTIES)
public class Mordant_jvm_jna_jvmTest {
    @Test
    fun serviceLoaderPublishesTheJnaTerminalInterfaceProvider() {
        val providers: List<TerminalInterfaceProvider> = ServiceLoader
            .load(TerminalInterfaceProvider::class.java)
            .toList()

        val jnaProviders: List<TerminalInterfaceProviderJna> = providers
            .filterIsInstance<TerminalInterfaceProviderJna>()

        assertThat(jnaProviders).hasSize(1)
    }

    @Test
    fun directProviderLoadExposesAUsableStandardTerminalInterfaceWhenSupported() {
        val terminalInterface: TerminalInterface? = TerminalInterfaceProviderJna().load()

        terminalInterface?.let { loaded ->
            val info = loaded.info(
                ansiLevel = AnsiLevel.NONE,
                hyperlinks = false,
                outputInteractive = false,
                inputInteractive = false,
            )
            val detectedSize = loaded.getTerminalSize()

            assertThat(info.ansiLevel).isEqualTo(AnsiLevel.NONE)
            assertThat(info.ansiHyperLinks).isFalse()
            assertThat(info.outputInteractive).isFalse()
            assertThat(info.inputInteractive).isFalse()
            assertThat(info.interactive).isFalse()
            detectedSize?.let { size ->
                assertThat(size.width).isGreaterThan(0)
                assertThat(size.height).isGreaterThan(0)
            }
            assertThat(loaded.shouldAutoUpdateSize()).isIn(true, false)
        } ?: assertThat(terminalInterface).isNull()
    }

    @Test
    fun providerReturnsNullForUnsupportedOperatingSystems() {
        val originalOsName: String? = System.getProperty("os.name")
        try {
            System.setProperty("os.name", "Mordant Test OS")

            val terminalInterface: TerminalInterface? = TerminalInterfaceProviderJna().load()

            assertThat(terminalInterface).isNull()
        } finally {
            if (originalOsName == null) {
                System.clearProperty("os.name")
            } else {
                System.setProperty("os.name", originalOsName)
            }
        }
    }

    @Test
    fun providerReturnsNullWhenRecognizedPlatformNativeLibraryCannotBeLinked() {
        val originalOsName: String? = System.getProperty("os.name")
        try {
            System.setProperty("os.name", "Windows Mordant Test")

            val terminalInterface: TerminalInterface? = TerminalInterfaceProviderJna().load()

            assertThat(terminalInterface).isNull()
        } finally {
            if (originalOsName == null) {
                System.clearProperty("os.name")
            } else {
                System.setProperty("os.name", originalOsName)
            }
        }
    }

    @Test
    fun providerReturnsNullWhenNativeImagePropertyIsSet() {
        val originalImageCode: String? = System.getProperty("org.graalvm.nativeimage.imagecode")
        try {
            val nativeImagePhases: List<String> = listOf("buildtime", "runtime")
            nativeImagePhases.forEach { phase ->
                System.setProperty("org.graalvm.nativeimage.imagecode", phase)

                val terminalInterface: TerminalInterface? = TerminalInterfaceProviderJna().load()

                assertThat(terminalInterface).isNull()
            }
        } finally {
            if (originalImageCode == null) {
                System.clearProperty("org.graalvm.nativeimage.imagecode")
            } else {
                System.setProperty("org.graalvm.nativeimage.imagecode", originalImageCode)
            }
        }
    }

    @Test
    fun terminalUsesTheStandardInterfaceResolvedFromRegisteredProviders() {
        val terminal = Terminal(
            ansiLevel = AnsiLevel.NONE,
            width = 42,
            height = 7,
            hyperlinks = false,
            interactive = false,
        )

        assertThat(terminal.terminalInfo.ansiLevel).isEqualTo(AnsiLevel.NONE)
        assertThat(terminal.terminalInfo.ansiHyperLinks).isFalse()
        assertThat(terminal.terminalInfo.outputInteractive).isFalse()
        assertThat(terminal.terminalInfo.inputInteractive).isFalse()
        assertThat(terminal.size.width).isEqualTo(42)
        assertThat(terminal.size.height).isEqualTo(7)
        assertThat(terminal.updateSize()).isEqualTo(terminal.size)
    }
}
