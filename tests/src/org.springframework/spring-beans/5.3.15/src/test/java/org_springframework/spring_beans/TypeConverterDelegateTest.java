/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.beans.SimpleTypeConverter;

import static org.assertj.core.api.Assertions.assertThat;

public class TypeConverterDelegateTest {
    @Test
    void convertsStringsEnumsArraysCollectionsAndMaps() {
        SimpleTypeConverter converter = new SimpleTypeConverter();
        assertThat(converter.convertIfNecessary("constructed", StringValue.class).getValue())
                .isEqualTo("constructed");
        assertThat(converter.convertIfNecessary(List.of("1", "2"), Integer[].class))
                .containsExactly(1, 2);
        assertThat(converter.convertIfNecessary(new String[] {"3", "4"}, Integer[].class))
                .containsExactly(3, 4);
        assertThat(converter.convertIfNecessary("5", Integer[].class)).containsExactly(5);

        ConversionBean bean = new ConversionBean();
        BeanWrapper wrapper = PropertyAccessorFactory.forBeanPropertyAccess(bean);
        wrapper.setPropertyValue("state", "READY");
        wrapper.setPropertyValue("rawState", State.class.getName() + ".DONE");
        wrapper.setPropertyValue("numbers", List.of("6", "7"));
        wrapper.setPropertyValue("mapping", Map.of("8", "9"));

        assertThat(bean.getState()).isEqualTo(State.READY);
        assertThat(bean.getRawState()).isEqualTo(State.DONE);
        assertThat(bean.getNumbers()).containsExactly(6, 7);
        assertThat(bean.getMapping()).containsEntry(8, 9);
    }

    public enum State {
        READY,
        DONE
    }

    public static class StringValue {
        private final String value;

        public StringValue(String value) {
            this.value = value;
        }

        public String getValue() {
            return this.value;
        }
    }

    public static class IntegerList extends ArrayList<Integer> {
        private static final long serialVersionUID = 1L;
    }

    public static class IntegerMap extends HashMap<Integer, Integer> {
        private static final long serialVersionUID = 1L;
    }

    public static class ConversionBean {
        private State state;
        private Enum<?> rawState;
        private IntegerList numbers;
        private IntegerMap mapping;

        public State getState() {
            return this.state;
        }

        public void setState(State state) {
            this.state = state;
        }

        public Enum<?> getRawState() {
            return this.rawState;
        }

        public void setRawState(Enum<?> rawState) {
            this.rawState = rawState;
        }

        public IntegerList getNumbers() {
            return this.numbers;
        }

        public void setNumbers(IntegerList numbers) {
            this.numbers = numbers;
        }

        public IntegerMap getMapping() {
            return this.mapping;
        }

        public void setMapping(IntegerMap mapping) {
            this.mapping = mapping;
        }
    }
}
