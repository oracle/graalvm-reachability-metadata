/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_util_ajax;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jetty.util.ajax.JSONPojoConvertor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JSONPojoConvertorInnerSetterTest {
    @Test
    void setsNullAndScalarValuesThroughPojoConvertor() {
        JSONPojoConvertor convertor = new JSONPojoConvertor(SetterTarget.class);

        SetterTarget target = convert(convertor, properties(
                "nullableText", null,
                "status", SetterTarget.Status.STARTED,
                "secondaryStatus", "STOPPED",
                "quantity", Long.valueOf(42L),
                "symbol", "Jetty",
                "label", "converted"));
        assertThat(target.nullableText).isNull();
        assertThat(target.status).isEqualTo(SetterTarget.Status.STARTED);
        assertThat(target.secondaryStatus).isEqualTo(SetterTarget.Status.STOPPED);
        assertThat(target.quantity).isEqualTo(42);
        assertThat(target.symbol).isEqualTo('J');
        assertThat(target.label).isEqualTo("converted");
    }

    @Test
    void copiesCompatibleObjectArraysToTypedPojoArrays() {
        JSONPojoConvertor convertor = new JSONPojoConvertor(SetterTarget.class);

        SetterTarget target = convert(convertor, properties(
                "names", new Object[]{"alpha", "beta"},
                "quantities", new Integer[]{3, 5, 8}));
        assertThat(target.names).containsExactly("alpha", "beta");
        assertThat(target.quantities).containsExactly(3, 5, 8);
    }

    @Test
    void fallsBackToOriginalNumericWrapperArrayWhenElementCannotBeConverted() {
        JSONPojoConvertor convertor = new JSONPojoConvertor(SetterTarget.class);
        Integer[] values = new Integer[]{1, null, 3};

        SetterTarget target = convert(convertor, properties("boxedQuantities", values));
        assertThat(target.boxedQuantities).isSameAs(values).containsExactly(1, null, 3);
    }

    @Test
    void fallsBackToOriginalObjectArrayWhenCopyCannotStoreValues() {
        JSONPojoConvertor convertor = new CopyFailureConvertor(SetterTarget.class);
        Object[] values = new Object[]{"plain-text"};

        SetterTarget target = convert(convertor, properties("objects", values));
        assertThat(target.objects).isSameAs(values).containsExactly("plain-text");
    }

    @Test
    void ignoresArrayValuesThatCannotBeAdaptedToPojoArrayTypes() {
        JSONPojoConvertor convertor = new JSONPojoConvertor(SetterTarget.class);

        SetterTarget target = convert(convertor, properties(
                "names", new Object[]{"alpha", Integer.valueOf(7)},
                "quantities", new Object[]{Integer.valueOf(1), "not-a-number"}));
        assertThat(target.names).isNull();
        assertThat(target.quantities).isNull();
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
        private Status status;
        private Status secondaryStatus;
        private int quantity;
        private char symbol;
        private String label;
        private String[] names;
        private int[] quantities;
        private Integer[] boxedQuantities;
        private Object[] objects;

        public void setNullableText(String nullableText) {
            this.nullableText = nullableText;
        }

        public void setStatus(Status status) {
            this.status = status;
        }

        public void setSecondaryStatus(Status secondaryStatus) {
            this.secondaryStatus = secondaryStatus;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        public void setSymbol(char symbol) {
            this.symbol = symbol;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public void setNames(String[] names) {
            this.names = names;
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

        public enum Status {
            STARTED,
            STOPPED
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
