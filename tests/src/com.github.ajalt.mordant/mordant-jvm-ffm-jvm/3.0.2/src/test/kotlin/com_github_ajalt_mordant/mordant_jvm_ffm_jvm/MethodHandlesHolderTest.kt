/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ajalt_mordant.mordant_jvm_ffm_jvm

import com.github.ajalt.mordant.terminal.TerminalInfo
import com.github.ajalt.mordant.terminal.TerminalInterface
import com.github.ajalt.mordant.terminal.terminalinterface.ffm.TerminalInterfaceProviderFfm
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

public class MethodHandlesHolderTest {
    @Test
    fun terminalInteractivityChecksResolveNativeMethodHandles(): Unit {
        val terminalInterface: TerminalInterface? = TerminalInterfaceProviderFfm().load()

        if (terminalInterface != null) {
            val info: TerminalInfo = terminalInterface.info(
                ansiLevel = null,
                hyperlinks = null,
                outputInteractive = null,
                inputInteractive = null,
            )

            assertThat(info.outputInteractive).isIn(true, false)
            assertThat(info.inputInteractive).isIn(true, false)
        } else {
            assertNull(terminalInterface)
        }
    }
}
