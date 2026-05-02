/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_xbean.xbean_reflect;

import org.apache.xbean.propertyeditor.EnumConverter;
import org.apache.xbean.recipe.Option;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EnumConverterTest {
    @Test
    void convertsOrdinalTextUsingEnumValuesMethod() {
        EnumConverter converter = new EnumConverter(Option.class);

        Object value = converter.toObject("2");

        assertThat(value).isEqualTo(Option.FIELD_INJECTION);
    }
}
