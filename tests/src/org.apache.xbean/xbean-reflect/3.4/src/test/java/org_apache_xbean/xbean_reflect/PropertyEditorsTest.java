/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_xbean.xbean_reflect;

import org.apache.xbean.propertyeditor.AbstractConverter;
import org.apache.xbean.propertyeditor.PropertyEditors;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertyEditorsTest {
    @Test
    void checksConversionSupportFromAClassName() {
        ClassLoader classLoader = PropertyEditorsTest.class.getClassLoader();

        boolean canConvert = PropertyEditors.canConvert(Integer.class.getName(), classLoader);

        assertThat(canConvert).isTrue();
    }

    @Test
    void convertsValueFromAClassName() {
        ClassLoader classLoader = PropertyEditorsTest.class.getClassLoader();

        Object value = PropertyEditors.getValue(Integer.class.getName(), "42", classLoader);

        assertThat(value).isEqualTo(Integer.valueOf(42));
    }

    @Test
    void discoversNestedConverterDeclaredOnTargetType() {
        Object value = PropertyEditors.getValue(ConverterBackedValue.class, "nested converter");

        assertThat(value).isInstanceOf(ConverterBackedValue.class);
        assertThat(((ConverterBackedValue) value).getText()).isEqualTo("nested converter");
        assertThat(PropertyEditors.toString(value)).isEqualTo("nested converter");
    }

    public static class ConverterBackedValue {
        private final String text;

        public ConverterBackedValue(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }

        @Override
        public String toString() {
            return text;
        }

        public static class ConverterBackedValueConverter extends AbstractConverter {
            public ConverterBackedValueConverter() {
                super(ConverterBackedValue.class);
            }

            @Override
            protected Object toObjectImpl(String text) {
                return new ConverterBackedValue(text);
            }
        }
    }
}
