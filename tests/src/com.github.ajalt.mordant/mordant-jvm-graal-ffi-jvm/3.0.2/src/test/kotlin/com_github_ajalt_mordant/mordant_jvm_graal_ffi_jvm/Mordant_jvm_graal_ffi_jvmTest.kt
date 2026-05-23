/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ajalt_mordant.mordant_jvm_graal_ffi_jvm

import com.github.ajalt.mordant.rendering.Size
import com.github.ajalt.mordant.terminal.PrintRequest
import com.github.ajalt.mordant.terminal.StandardTerminalInterface
import com.github.ajalt.mordant.terminal.TerminalInfo
import com.github.ajalt.mordant.terminal.TerminalInterface
import com.github.ajalt.mordant.terminal.TerminalInterfaceProvider
import com.github.ajalt.mordant.terminal.terminalinterface.nativeimage.TerminalInterfaceProviderNativeImage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.util.ServiceLoader

public class TerminalInterfaceProviderNativeImageTest {
    @Test
    fun providerImplementsMordantTerminalInterfaceProvider(): Unit {
        val provider = TerminalInterfaceProviderNativeImage()

        assertThat(provider).isInstanceOf(TerminalInterfaceProvider::class.java)
    }

    @Test
    fun serviceLoaderDiscoversGraalNativeTerminalProvider(): Unit {
        val providers: List<TerminalInterfaceProvider> = ServiceLoader
            .load(TerminalInterfaceProvider::class.java)
            .toList()
        val graalProviders: List<TerminalInterfaceProviderNativeImage> =
            providers.filterIsInstance<TerminalInterfaceProviderNativeImage>()

        assertThat(providers).isNotEmpty()
        assertThat(graalProviders).hasSize(1)
    }

    @Test
    fun terminalInterfaceCompletesPrintRequestsToSelectedStreams(): Unit {
        val terminalInterface: TerminalInterface? = TerminalInterfaceProviderNativeImage().load()

        if (terminalInterface == null) {
            assertThat(terminalInterface === null).isTrue()
        } else {
            val stdoutBuffer = ByteArrayOutputStream()
            val stderrBuffer = ByteArrayOutputStream()
            val originalOut = System.out
            val originalErr = System.err

            try {
                val stdoutStream = PrintStream(stdoutBuffer, true, StandardCharsets.UTF_8)
                val stderrStream = PrintStream(stderrBuffer, true, StandardCharsets.UTF_8)
                System.setOut(stdoutStream)
                System.setErr(stderrStream)

                terminalInterface.completePrintRequest(
                    PrintRequest(
                        text = "standard output",
                        trailingLinebreak = false,
                        stderr = false,
                    ),
                )
                terminalInterface.completePrintRequest(
                    PrintRequest(text = "standard error", trailingLinebreak = true, stderr = true),
                )

                stdoutStream.flush()
                stderrStream.flush()
            } finally {
                System.setOut(originalOut)
                System.setErr(originalErr)
            }

            assertThat(stdoutBuffer.toString(StandardCharsets.UTF_8)).isEqualTo("standard output")
            assertThat(stderrBuffer.toString(StandardCharsets.UTF_8))
                .isEqualTo("standard error${System.lineSeparator()}")
        }
    }

    @Test
    fun providerLoadSuppliesTerminalInterfaceWhenNativeBindingsAreAvailable(): Unit {
        val terminalInterface: TerminalInterface? = TerminalInterfaceProviderNativeImage().load()

        if (terminalInterface == null) {
            assertThat(terminalInterface === null).isTrue()
        } else {
            assertThat(terminalInterface).isInstanceOf(StandardTerminalInterface::class.java)
            val standardTerminalInterface = terminalInterface as StandardTerminalInterface
            val inputInteractive: Boolean = standardTerminalInterface.stdinInteractive()
            val outputInteractive: Boolean = standardTerminalInterface.stdoutInteractive()
            val terminalInfo: TerminalInfo = terminalInterface.info(
                ansiLevel = null,
                hyperlinks = null,
                outputInteractive = outputInteractive,
                inputInteractive = inputInteractive,
            )

            assertThat(terminalInfo.inputInteractive).isEqualTo(inputInteractive)
            assertThat(terminalInfo.outputInteractive).isEqualTo(outputInteractive)
            assertThat(terminalInterface.shouldAutoUpdateSize()).isTrue()

            val terminalSize: Size? = terminalInterface.getTerminalSize()
            if (terminalSize == null) {
                assertThat(terminalSize === null).isTrue()
            } else {
                assertThat(terminalSize.width).isPositive()
                assertThat(terminalSize.height).isPositive()
            }
        }
    }
}
