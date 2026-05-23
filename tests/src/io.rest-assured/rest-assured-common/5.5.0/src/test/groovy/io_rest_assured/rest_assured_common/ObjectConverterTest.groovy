/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured_common

import io.restassured.internal.common.path.ObjectConverter
import org.junit.jupiter.api.Test

import java.math.BigDecimal
import java.util.UUID

import static org.assertj.core.api.Assertions.assertThat

public class ObjectConverterTest {
    @Test
    void convertsTextValuesToSupportedTargetTypes() {
        UUID uuid = UUID.fromString('123e4567-e89b-12d3-a456-426614174000')

        assertThat(ObjectConverter.convertObjectTo('42', Integer)).isEqualTo(42)
        assertThat(ObjectConverter.convertObjectTo('true', Boolean)).isEqualTo(true)
        assertThat(ObjectConverter.convertObjectTo('A', Character)).isEqualTo('A' as Character)
        assertThat(ObjectConverter.convertObjectTo('7', Byte)).isEqualTo((byte) 7)
        assertThat(ObjectConverter.convertObjectTo('8', Short)).isEqualTo((short) 8)
        assertThat(ObjectConverter.convertObjectTo('1.5', Float)).isEqualTo(1.5f)
        assertThat(ObjectConverter.convertObjectTo('2.5', Double)).isEqualTo(2.5d)
        assertThat(ObjectConverter.convertObjectTo('9000', Long)).isEqualTo(9000L)
        assertThat(ObjectConverter.convertObjectTo('12.75', BigDecimal)).isEqualByComparingTo(new BigDecimal('12.75'))
        assertThat(ObjectConverter.convertObjectTo(123, String)).isEqualTo('123')
        assertThat(ObjectConverter.convertObjectTo(uuid.toString(), UUID)).isEqualTo(uuid)
    }

    @Test
    void returnsNullWhenConvertingNullValue() {
        assertThat(ObjectConverter.convertObjectTo(null, String)).isNull()
    }

    @Test
    void returnsOriginalValueWhenItAlreadyMatchesTargetType() {
        String value = 'unchanged'

        assertThat(ObjectConverter.convertObjectTo(value, String)).isSameAs(value)
    }

    @Test
    void reportsWhetherValuesCanBeConverted() {
        assertThat(ObjectConverter.canConvert('11', Integer)).isTrue()
        assertThat(ObjectConverter.canConvert('not-a-number', Integer)).isFalse()
    }
}
