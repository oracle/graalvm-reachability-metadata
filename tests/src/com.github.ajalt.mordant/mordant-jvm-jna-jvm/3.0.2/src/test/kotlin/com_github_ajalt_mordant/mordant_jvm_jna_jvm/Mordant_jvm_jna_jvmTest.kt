/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ajalt_mordant.mordant_jvm_jna_jvm

import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.PrintRequest
import com.github.ajalt.mordant.terminal.TerminalInterface
import com.github.ajalt.mordant.terminal.TerminalInterfaceProvider
import com.github.ajalt.mordant.terminal.terminalinterface.jna.TerminalInterfaceProviderJna
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.util.ServiceLoader

public class Mordant_jvm_jna_jvmTest {
    @Test
    fun `provider is discoverable through service loader`() {
        val providers: List<TerminalInterfaceProvider> = ServiceLoader
            .load(TerminalInterfaceProvider::class.java)
            .toList()

        assertThat(providers)
            .anySatisfy { provider: TerminalInterfaceProvider ->
                assertThat(provider).isInstanceOf(TerminalInterfaceProviderJna::class.java)
            }
    }

    @Test
    fun `provider declines unsupported operating systems safely`() {
        val propertyName: String = "os.name"
        val originalOsName: String? = System.getProperty(propertyName)
        try {
            System.setProperty(propertyName, "Mordant Unsupported OS")

            val terminalInterface: TerminalInterface? = TerminalInterfaceProviderJna().load()

            assertThat(terminalInterface).isNull()
        } finally {
            if (originalOsName == null) {
                System.clearProperty(propertyName)
            } else {
                System.setProperty(propertyName, originalOsName)
            }
        }
    }

    @Test
    fun `provider declines native image execution markers`() {
        val propertyName: String = "org.graalvm.nativeimage.imagecode"
        val originalImageCode: String? = System.getProperty(propertyName)
        try {
            listOf("buildtime", "runtime").forEach { imageCode: String ->
                System.setProperty(propertyName, imageCode)

                val terminalInterface: TerminalInterface? = TerminalInterfaceProviderJna().load()

                assertThat(terminalInterface).isNull()
            }
        } finally {
            if (originalImageCode == null) {
                System.clearProperty(propertyName)
            } else {
                System.setProperty(propertyName, originalImageCode)
            }
        }
    }

    @Test
    fun `provider loads current platform terminal or declines without error`() {
        val terminalInterface: TerminalInterface? = TerminalInterfaceProviderJna().load()

        if (terminalInterface == null) {
            assertThat(terminalInterface == null).isTrue()
            return
        }

        val info = terminalInterface.info(
            ansiLevel = AnsiLevel.TRUECOLOR,
            hyperlinks = false,
            outputInteractive = false,
            inputInteractive = true,
        )
        assertThat(info.ansiLevel).isEqualTo(AnsiLevel.TRUECOLOR)
        assertThat(info.ansiHyperLinks).isFalse()
        assertThat(info.outputInteractive).isFalse()
        assertThat(info.inputInteractive).isTrue()
        assertThat(info.interactive).isFalse()

        val terminalSize = terminalInterface.getTerminalSize()
        if (terminalSize != null) {
            assertThat(terminalSize.width).isGreaterThanOrEqualTo(0)
            assertThat(terminalSize.height).isGreaterThanOrEqualTo(0)
        }

        assertThatCode {
            captureStandardOut {
                terminalInterface.completePrintRequest(
                    PrintRequest(text = "mordant-jna", trailingLinebreak = true, stderr = false),
                )
            }
        }.doesNotThrowAnyException()
    }

    @Test
    fun `loaded terminal writes print requests to standard output`() {
        val terminalInterface: TerminalInterface? = TerminalInterfaceProviderJna().load()

        if (terminalInterface == null) {
            assertThat(terminalInterface == null).isTrue()
            return
        }

        val printed: String = captureStandardOut {
            terminalInterface.completePrintRequest(
                PrintRequest(text = "mordant-jna-output", trailingLinebreak = true, stderr = false),
            )
        }

        assertThat(printed.replace(System.lineSeparator(), "\n"))
            .isEqualTo("mordant-jna-output\n")
    }

    @Test
    fun `loaded terminal writes print requests to standard error without trailing linebreak`() {
        val terminalInterface: TerminalInterface? = TerminalInterfaceProviderJna().load()

        if (terminalInterface == null) {
            assertThat(terminalInterface == null).isTrue()
            return
        }

        val captured: CapturedStreams = captureStandardStreams {
            terminalInterface.completePrintRequest(
                PrintRequest(text = "mordant-jna-error", trailingLinebreak = false, stderr = true),
            )
        }

        assertThat(captured.stdout).isEmpty()
        assertThat(captured.stderr.replace(System.lineSeparator(), "\n"))
            .isEqualTo("mordant-jna-error")
    }

    private fun captureStandardOut(block: () -> Unit): String {
        val originalOut: PrintStream = System.out
        val bytes = ByteArrayOutputStream()
        val capturingOut = PrintStream(bytes, true, StandardCharsets.UTF_8.name())
        try {
            System.setOut(capturingOut)
            block()
        } finally {
            capturingOut.flush()
            System.setOut(originalOut)
            capturingOut.close()
        }
        return bytes.toString(StandardCharsets.UTF_8.name())
    }

    private fun captureStandardStreams(block: () -> Unit): CapturedStreams {
        val originalOut: PrintStream = System.out
        val originalErr: PrintStream = System.err
        val outBytes = ByteArrayOutputStream()
        val errBytes = ByteArrayOutputStream()
        val capturingOut = PrintStream(outBytes, true, StandardCharsets.UTF_8.name())
        val capturingErr = PrintStream(errBytes, true, StandardCharsets.UTF_8.name())
        try {
            System.setOut(capturingOut)
            System.setErr(capturingErr)
            block()
        } finally {
            capturingOut.flush()
            capturingErr.flush()
            System.setOut(originalOut)
            System.setErr(originalErr)
            capturingOut.close()
            capturingErr.close()
        }
        return CapturedStreams(
            stdout = outBytes.toString(StandardCharsets.UTF_8.name()),
            stderr = errBytes.toString(StandardCharsets.UTF_8.name()),
        )
    }

    private data class CapturedStreams(
        val stdout: String,
        val stderr: String,
    )
}
