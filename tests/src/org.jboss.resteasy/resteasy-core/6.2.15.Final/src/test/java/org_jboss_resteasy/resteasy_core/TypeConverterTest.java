/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_core;

import java.math.BigDecimal;
import java.net.URI;

import org.jboss.resteasy.util.TypeConverter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TypeConverterTest {
    @Test
    void convertsValueOfStringConstructorsAndPrimitiveAccessors() {
        assertThat(TypeConverter.getType(int.class, "37")).isEqualTo(37);
        assertThat(TypeConverter.getType(BigDecimal.class, "12.50")).isEqualByComparingTo("12.50");
        assertThat(TypeConverter.getType(URI.class, "https://example.test/items/7")).isEqualTo(URI.create("https://example.test/items/7"));
        assertThat(TypeConverter.getType(char.class, "xyz")).isEqualTo('x');
        assertThat(TypeConverter.getType(boolean.class, "Yes")).isTrue();
        assertThat(TypeConverter.getType(boolean.class, "0")).isFalse();
        assertThat(TypeConverter.isConvertable(BigDecimal.class)).isTrue();
        assertThat(TypeConverter.isConvertable(Integer.class)).isTrue();
        assertThat(TypeConverter.isConvertable(Object.class)).isFalse();
    }
}
