/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools_jackson_module.jackson_module_kotlin

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

public class MethodValueCreatorInnerCompanionTest {
    @Test
    fun deserializesUsingJvmStaticCreatorFromPrivateCompanionObject(): Unit {
        val result: PrivateCompanionFactoryValue = jacksonObjectMapper()
            .readValue("""{"value":"alpha"}""")

        assertThat(result.value).isEqualTo("alpha")
    }
}

class PrivateCompanionFactoryValue private constructor(
    @get:JsonProperty("value")
    val value: String
) {
    private companion object {
        @JvmStatic
        @JsonCreator
        fun create(@JsonProperty("value") value: String): PrivateCompanionFactoryValue =
            PrivateCompanionFactoryValue(value)
    }
}
