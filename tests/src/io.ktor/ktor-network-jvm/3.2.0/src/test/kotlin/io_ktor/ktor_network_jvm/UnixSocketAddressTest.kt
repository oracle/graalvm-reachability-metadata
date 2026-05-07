/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_network_jvm

import io.ktor.network.sockets.UnixSocketAddress
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

public class UnixSocketAddressTest {
    @Test
    public fun exposesUnixDomainSocketPath(): Unit {
        val temporaryDirectory: Path = Files.createTempDirectory("ktor-network-unix-address-")
        val socketPath: Path = temporaryDirectory.resolve("server.sock")

        try {
            val address: UnixSocketAddress = UnixSocketAddress(socketPath.toString())

            assertThat(address.path).isEqualTo(socketPath.toString())
            assertThat(address.component1()).isEqualTo(socketPath.toString())
        } finally {
            Files.deleteIfExists(socketPath)
            Files.deleteIfExists(temporaryDirectory)
        }
    }
}
