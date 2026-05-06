/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ajalt_mordant.mordant_jvm_graal_ffi_jvm

import com.github.ajalt.mordant.rendering.Size
import com.github.ajalt.mordant.terminal.TerminalInfo
import com.github.ajalt.mordant.terminal.TerminalInterface
import com.github.ajalt.mordant.terminal.TerminalInterfaceProvider
import com.github.ajalt.mordant.terminal.terminalinterface.nativeimage.TerminalInterfaceProviderNativeImage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.ServiceLoader

public class Mordant_jvm_graal_ffi_jvmTest {
    @Test
    fun test() {
        println("This is just a placeholder, implement your test")
    }

    @Test
    fun providerIsDiscoverableThroughServiceLoader(): Unit {
        val providers: List<TerminalInterfaceProvider> = ServiceLoader
            .load(TerminalInterfaceProvider::class.java)
            .toList()

        assertThat(providers)
            .anySatisfy { provider: TerminalInterfaceProvider ->
                assertThat(provider).isInstanceOf(TerminalInterfaceProviderNativeImage::class.java)
            }
    }

    @Test
    fun providerReportsNativeTerminalCapabilitiesWhenLoaded(): Unit {
        val terminalInterface: TerminalInterface? = TerminalInterfaceProviderNativeImage().load()

        if (terminalInterface != null) {
            val info: TerminalInfo = terminalInterface.info(
                ansiLevel = null,
                hyperlinks = null,
                outputInteractive = null,
                inputInteractive = null,
            )
            val terminalSize: Size? = terminalInterface.getTerminalSize()

            assertThat(info.outputInteractive).isIn(true, false)
            assertThat(info.inputInteractive).isIn(true, false)
            assertThat(terminalInterface.shouldAutoUpdateSize()).isTrue()
            if (terminalSize != null) {
                assertThat(terminalSize.width).isGreaterThan(0)
                assertThat(terminalSize.height).isGreaterThan(0)
            }
        } else {
            assertNull(terminalInterface)
        }
    }
}
