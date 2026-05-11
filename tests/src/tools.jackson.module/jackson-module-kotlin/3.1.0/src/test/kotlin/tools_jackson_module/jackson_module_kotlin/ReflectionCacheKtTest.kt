/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools_jackson_module.jackson_module_kotlin

import com.fasterxml.jackson.annotation.JsonProperty
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

public class ReflectionCacheKtTest {
    @Test
    fun deserializesPrimaryConstructorWithValueClassParameter(): Unit {
        val result: ReflectionCacheValueClassHolder = jacksonObjectMapper()
            .readValue("""{"wrapped":"alpha"}""")

        assertThat(result.wrapped.value).isEqualTo("alpha")
    }
}

@JvmInline
value class ReflectionCacheInlineValue(val value: String)

class ReflectionCacheValueClassHolder(
    @param:JsonProperty("wrapped")
    @get:JsonProperty("wrapped")
    val wrapped: ReflectionCacheInlineValue
)
