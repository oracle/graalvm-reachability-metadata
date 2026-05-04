/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ajalt_mordant.mordant_jvm_jna_jvm

import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.Size
import com.github.ajalt.mordant.terminal.TerminalInfo
import com.github.ajalt.mordant.terminal.TerminalInterface
import com.github.ajalt.mordant.terminal.TerminalInterfaceProvider
import com.github.ajalt.mordant.terminal.terminalinterface.jna.TerminalInterfaceProviderJna
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.ServiceLoader

public class MordantJvmJnaJvmTest {
    @Test
    fun providerIsRegisteredForServiceLoaderDiscovery(): Unit {
        val providers: List<TerminalInterfaceProvider> = ServiceLoader
            .load(TerminalInterfaceProvider::class.java)
            .toList()

        assertThat(providers.any { provider: TerminalInterfaceProvider ->
            provider is TerminalInterfaceProviderJna
        }).isTrue()
    }

    @Test
    fun providerReturnsNullWhenNativeImageModeIsSignaled(): Unit {
        withSystemProperty("org.graalvm.nativeimage.imagecode", "runtime") {
            val terminalInterface: TerminalInterface? = TerminalInterfaceProviderJna().load()

            assertThat(terminalInterface).isNull()
        }
    }

    @Test
    fun providerReturnsNullForUnsupportedOperatingSystems(): Unit {
        withSystemProperty("os.name", "Plan 9") {
            val terminalInterface: TerminalInterface? = TerminalInterfaceProviderJna().load()

            assertThat(terminalInterface).isNull()
        }
    }

    @Test
    fun loadedProviderReportsTerminalInformationWithExplicitOverrides(): Unit {
        val terminalInterface: TerminalInterface? = TerminalInterfaceProviderJna().load()

        if (terminalInterface == null) {
            assertThat(providerMayBeUnavailable()).isTrue()
        } else {
            val info: TerminalInfo = terminalInterface.info(
                ansiLevel = AnsiLevel.TRUECOLOR,
                hyperlinks = true,
                outputInteractive = false,
                inputInteractive = true,
            )

            assertThat(info.ansiLevel).isEqualTo(AnsiLevel.TRUECOLOR)
            assertThat(info.ansiHyperLinks).isTrue()
            assertThat(info.outputInteractive).isFalse()
            assertThat(info.inputInteractive).isTrue()
            assertThat(info.interactive).isFalse()
            assertThat(info.supportsAnsiCursor).isIn(true, false)
        }
    }

    @Test
    fun loadedProviderReturnsTerminalSizeOrUnknownWithValidDimensions(): Unit {
        val terminalInterface: TerminalInterface? = TerminalInterfaceProviderJna().load()

        if (terminalInterface == null) {
            assertThat(providerMayBeUnavailable()).isTrue()
        } else {
            val size: Size? = terminalInterface.getTerminalSize()

            if (size != null) {
                assertThat(size.width).isPositive()
                assertThat(size.height).isPositive()
            }
            assertThat(terminalInterface.shouldAutoUpdateSize()).isIn(true, false)
        }
    }

    private fun providerMayBeUnavailable(): Boolean {
        val imageCode: String? = System.getProperty("org.graalvm.nativeimage.imagecode")
        val osName: String = System.getProperty("os.name")
        return imageCode == "buildtime" || imageCode == "runtime" || !isSupportedOperatingSystem(osName)
    }

    private fun <T> withSystemProperty(key: String, value: String, block: () -> T): T {
        val previousValue: String? = System.getProperty(key)
        System.setProperty(key, value)
        try {
            return block()
        } finally {
            if (previousValue == null) {
                System.clearProperty(key)
            } else {
                System.setProperty(key, previousValue)
            }
        }
    }

    private fun isSupportedOperatingSystem(osName: String): Boolean {
        return osName == "Linux" || osName == "Mac OS X" || osName.startsWith("Windows")
    }
}
