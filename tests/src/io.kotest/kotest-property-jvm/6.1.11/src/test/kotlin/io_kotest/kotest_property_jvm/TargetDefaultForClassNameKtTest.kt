/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kotest.kotest_property_jvm

import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.bind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.DayOfWeek

public class TargetDefaultForClassNameKtTest {
    @Test
    fun bindCreatesArbitraryForEnumClass(): Unit {
        val arb: Arb<DayOfWeek> = Arb.bind()

        val sample: DayOfWeek = arb.sample(RandomSource.seeded(1L)).value

        assertThat(sample).isIn(*DayOfWeek.values())
    }

    @Test
    fun bindCreatesArbitraryForSetWithEnumElementType(): Unit {
        val arb: Arb<Set<DayOfWeek>> = Arb.bind()

        val sample: Set<DayOfWeek> = arb.sample(RandomSource.seeded(2L)).value

        assertThat(sample).hasSizeLessThanOrEqualTo(DayOfWeek.values().size)
        assertThat(sample).allSatisfy { day: DayOfWeek ->
            assertThat(day).isIn(*DayOfWeek.values())
        }
    }
}
