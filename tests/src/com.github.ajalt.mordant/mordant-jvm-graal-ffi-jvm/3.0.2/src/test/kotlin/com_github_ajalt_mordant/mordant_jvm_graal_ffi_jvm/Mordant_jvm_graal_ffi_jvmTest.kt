/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ajalt_mordant.mordant_jvm_graal_ffi_jvm

import com.github.ajalt.mordant.terminal.TerminalInterfaceProvider
import com.github.ajalt.mordant.terminal.terminalinterface.nativeimage.TerminalInterfaceProviderNativeImage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
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
}
