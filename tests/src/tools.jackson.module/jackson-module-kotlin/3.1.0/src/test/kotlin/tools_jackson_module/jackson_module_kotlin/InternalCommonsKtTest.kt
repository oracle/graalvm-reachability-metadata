/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools_jackson_module.jackson_module_kotlin

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper

public class InternalCommonsKtTest {
    @Test
    fun serializesKotlinValueClassThroughGeneratedUnboxMethod(): Unit {
        val mapper = jacksonObjectMapper()
        val payload = InternalCommonsInlineUserId("alpha")

        val json = mapper.writeValueAsString(payload)

        assertThat(json).isEqualTo("\"alpha\"")
    }
}

@JvmInline
value class InternalCommonsInlineUserId(val value: String)
