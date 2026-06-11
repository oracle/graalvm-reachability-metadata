/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_mockk.mockk_jvm

import io.mockk.impl.InternalPlatform
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class InternalPlatformTest {
    @Test
    fun `copyFields copies private fields from the complete class hierarchy`(): Unit {
        val source: InternalPlatformDerivedFields = InternalPlatformDerivedFields("base-source", "child-source")
        val destination: InternalPlatformDerivedFields = InternalPlatformDerivedFields("base-destination", "child-destination")

        InternalPlatform.copyFields(destination, source)

        assertThat(destination.values()).isEqualTo(listOf("base-source", "child-source"))
    }
}

public open class InternalPlatformBaseFields(
    private var baseValue: String,
) {
    public fun baseValue(): String = baseValue
}

public class InternalPlatformDerivedFields(
    baseValue: String,
    private var childValue: String,
) : InternalPlatformBaseFields(baseValue) {
    public fun values(): List<String> = listOf(baseValue(), childValue)
}
