/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools_jackson_module.jackson_module_kotlin

import com.fasterxml.jackson.annotation.JsonKey
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper

public class ValueClassStaticJsonKeySerializerInnerCompanionTest {
    @Test
    fun serializesValueClassMapKeyUsingStaticJsonKeyGetter(): Unit {
        val mapper = jacksonObjectMapper()
        val payload: Map<ValueClassStaticJsonKeyUserId, String> = mapOf(
            ValueClassStaticJsonKeyUserId("alpha") to "payload"
        )

        val json: String = mapper.writeValueAsString(payload)

        assertThat(json).isEqualTo("""{"user-alpha":"payload"}""")
    }
}

@JvmInline
value class ValueClassStaticJsonKeyUserId(val value: String) {
    @get:JsonKey
    val jsonKey: String
        get() = "user-$value"
}
