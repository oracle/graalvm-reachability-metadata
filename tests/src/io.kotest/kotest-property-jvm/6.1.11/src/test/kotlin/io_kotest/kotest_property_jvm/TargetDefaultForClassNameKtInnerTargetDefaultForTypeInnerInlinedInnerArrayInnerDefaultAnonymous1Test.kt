/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kotest.kotest_property_jvm

import io.kotest.property.Arb
import io.kotest.property.PropertyTesting
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.targetDefaultForType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.reflect.typeOf

public class TargetDefaultForClassNameKtInnerTargetDefaultForTypeInnerInlinedInnerArrayInnerDefaultAnonymous1Test {
    @Test
    fun targetDefaultForTypeCreatesTypedObjectArray(): Unit {
        val originalRange: IntRange = PropertyTesting.defaultCollectionsRange
        try {
            PropertyTesting.defaultCollectionsRange = 2..2
            val providedArbs: Map<KClass<*>, Arb<*>> = mapOf(String::class to Arb.constant("kotest"))
            @Suppress("UNCHECKED_CAST")
            val arb: Arb<Array<String>> = targetDefaultForType(
                providedArbs = providedArbs,
                arbsForProps = emptyMap(),
                type = typeOf<Array<String>>()
            ) as Arb<Array<String>>

            val sample: Array<String> = arb.sample(RandomSource.seeded(3L)).value
            val edgecase: Array<String> = arb.edgecase(RandomSource.seeded(4L))!!.value

            assertKotestArray(sample)
            assertKotestArray(edgecase)
        } finally {
            PropertyTesting.defaultCollectionsRange = originalRange
        }
    }

    private fun assertKotestArray(values: Array<String>): Unit {
        assertThat(values.javaClass.componentType).isEqualTo(String::class.java)
        assertThat(values).hasSize(2)
        assertThat(values).allSatisfy { value: String ->
            assertThat(value).isEqualTo("kotest")
        }
    }
}
