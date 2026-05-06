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
import tools.jackson.module.kotlin.readValue

public class KotlinDeserializersKtTest {
    @Test
    fun deserializesTopLevelKotlinValueClass(): Unit {
        val result: KotlinDeserializersInlineUserId = jacksonObjectMapper()
            .readValue("\"alpha\"")

        assertThat(result.value).isEqualTo("alpha")
    }
}

@JvmInline
value class KotlinDeserializersInlineUserId(val value: String)
