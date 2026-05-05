/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.core.util.BeanMap;
import org.junit.jupiter.api.Test;

public class BeanMapTest {

    @Test
    void putInvokesBeanAccessorsAndConvertsValueWithSingleArgumentConstructor() {
        SampleBean bean = new SampleBean();
        BeanMap map = new BeanMap(bean);

        Object oldValue = map.put("converted", "updated");

        assertThat(oldValue).isEqualTo(new ConvertedValue("default-converted"));
        assertThat(bean.getConverted()).isEqualTo(new ConvertedValue("updated"));
        assertThat(map.get("converted")).isEqualTo(new ConvertedValue("updated"));
    }

    @Test
    void cloneCreatesNewBeanInstanceAndCopiesReadableWritableProperties() throws CloneNotSupportedException {
        SampleBean bean = new SampleBean();
        BeanMap map = new BeanMap(bean);
        map.put("text", "original");
        map.put("number", 42);
        map.put("converted", new ConvertedValue("copied"));

        BeanMap clone = (BeanMap) map.clone();
        SampleBean clonedBean = (SampleBean) clone.getBean();

        assertThat(clonedBean).isNotSameAs(bean);
        assertThat(clone.get("text")).isEqualTo("original");
        assertThat(clone.get("number")).isEqualTo(42);
        assertThat(clone.get("converted")).isEqualTo(new ConvertedValue("copied"));
    }

    @Test
    void clearReplacesBeanWithDefaultInstance() {
        SampleBean bean = new SampleBean();
        BeanMap map = new BeanMap(bean);
        map.put("text", "changed");
        map.put("number", 99);
        map.put("converted", new ConvertedValue("changed"));

        map.clear();

        assertThat(map.getBean()).isNotSameAs(bean);
        assertThat(map.get("text")).isEqualTo("default");
        assertThat(map.get("number")).isEqualTo(7);
        assertThat(map.get("converted")).isEqualTo(new ConvertedValue("default-converted"));
    }

    public static final class SampleBean {
        private String text = "default";
        private int number = 7;
        private ConvertedValue converted = new ConvertedValue("default-converted");

        public SampleBean() {
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public int getNumber() {
            return number;
        }

        public void setNumber(int number) {
            this.number = number;
        }

        public ConvertedValue getConverted() {
            return converted;
        }

        public void setConverted(ConvertedValue converted) {
            this.converted = converted;
        }
    }

    public static final class ConvertedValue {
        private final String value;

        public ConvertedValue(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof ConvertedValue)) {
                return false;
            }
            ConvertedValue that = (ConvertedValue) object;
            return value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }
}
