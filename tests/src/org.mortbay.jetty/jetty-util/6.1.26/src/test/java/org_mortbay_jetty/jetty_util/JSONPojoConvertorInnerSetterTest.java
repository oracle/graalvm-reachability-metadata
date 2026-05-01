/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mortbay_jetty.jetty_util;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mortbay.util.ajax.JSONPojoConvertor;

import static org.assertj.core.api.Assertions.assertThat;

public class JSONPojoConvertorInnerSetterTest {
    @Test
    void setsNullScalarAndPlainObjectValuesThroughPojoSetters() {
        JSONPojoConvertor convertor = new JSONPojoConvertor(SetterTarget.class);

        SetterTarget target = convert(convertor, properties(
                "nullableText", null,
                "quantity", Long.valueOf(42L),
                "label", "converted"));

        assertThat(target.nullableText).isNull();
        assertThat(target.quantity).isEqualTo(42);
        assertThat(target.label).isEqualTo("converted");
    }

    @Test
    void copiesCompatibleObjectArraysToTypedPojoArrays() {
        JSONPojoConvertor convertor = new JSONPojoConvertor(SetterTarget.class);

        SetterTarget target = convert(convertor, properties("names", new Object[]{"alpha", "beta"}));

        assertThat(target.names).containsExactly("alpha", "beta");
    }

    @Test
    void ignoresObjectArraysThatCannotBeCopiedToPojoArrayTypes() {
        JSONPojoConvertor convertor = new JSONPojoConvertor(SetterTarget.class);

        SetterTarget target = convert(convertor, properties("descriptions", new Object[]{"alpha", Integer.valueOf(7)}));

        assertThat(target.descriptions).isNull();
    }

    @Test
    void fallsBackToOriginalObjectArrayWhenConfiguredComponentCopyFails() {
        JSONPojoConvertor convertor = new CopyFailureConvertor(SetterTarget.class);
        Object[] values = new Object[]{"plain-text"};

        SetterTarget target = convert(convertor, properties("objects", values));

        assertThat(target.objects).isSameAs(values).containsExactly("plain-text");
    }

    @Test
    void convertsNumericObjectArraysToPrimitivePojoArrays() {
        JSONPojoConvertor convertor = new JSONPojoConvertor(SetterTarget.class);

        SetterTarget target = convert(convertor, properties(
                "quantities", new Object[]{Long.valueOf(3L), Integer.valueOf(5), Short.valueOf((short) 8)}));

        assertThat(target.quantities).containsExactly(3, 5, 8);
    }

    @Test
    void fallsBackToOriginalNumericWrapperArrayWhenElementCannotBeConverted() {
        JSONPojoConvertor convertor = new JSONPojoConvertor(SetterTarget.class);
        Integer[] values = new Integer[]{Integer.valueOf(1), null, Integer.valueOf(3)};

        SetterTarget target = convert(convertor, properties("boxedQuantities", values));

        assertThat(target.boxedQuantities).isSameAs(values).containsExactly(1, null, 3);
    }

    private static SetterTarget convert(JSONPojoConvertor convertor, Map<String, Object> properties) {
        Object converted = convertor.fromJSON(properties);

        assertThat(converted).isInstanceOf(SetterTarget.class);
        return (SetterTarget) converted;
    }

    private static Map<String, Object> properties(Object... entries) {
        Map<String, Object> properties = new HashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            properties.put((String) entries[index], entries[index + 1]);
        }
        return properties;
    }

    public static class SetterTarget {
        private String nullableText = "initial";
        private int quantity;
        private String label;
        private String[] names;
        private CharSequence[] descriptions;
        private int[] quantities;
        private Integer[] boxedQuantities;
        private Object[] objects;

        public void setNullableText(String nullableText) {
            this.nullableText = nullableText;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public void setNames(String[] names) {
            this.names = names;
        }

        public void setDescriptions(CharSequence[] descriptions) {
            this.descriptions = descriptions;
        }

        public void setQuantities(int[] quantities) {
            this.quantities = quantities;
        }

        public void setBoxedQuantities(Integer[] boxedQuantities) {
            this.boxedQuantities = boxedQuantities;
        }

        public void setObjects(Object[] objects) {
            this.objects = objects;
        }
    }

    public static class CopyFailureConvertor extends JSONPojoConvertor {
        public CopyFailureConvertor(Class<?> pojoClass) {
            super(pojoClass);
        }

        @Override
        protected void addSetter(String name, Method method) {
            if ("objects".equals(name)) {
                _setters.put(name, new CopyFailureSetter(name, method));
                return;
            }
            super.addSetter(name, method);
        }
    }

    public static class CopyFailureSetter extends JSONPojoConvertor.Setter {
        public CopyFailureSetter(String propertyName, Method method) {
            super(propertyName, method);
            _componentType = StringBuilder.class;
        }
    }
}
