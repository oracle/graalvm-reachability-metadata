/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_core;

import com.querydsl.core.util.BeanMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanMapTest {
    @Test
    void getAndPutInvokeBeanAccessorsAndConvertValuesWithSingleArgumentConstructor() {
        SampleBean bean = new SampleBean();
        BeanMap map = new BeanMap(bean);

        Object oldLabel = map.put("label", "updated");
        Object oldNumberBox = map.put("numberBox", "42");

        assertThat(oldLabel).isEqualTo("initial");
        assertThat(oldNumberBox).isEqualTo(new NumberBox("7"));
        assertThat(map.get("label")).isEqualTo("updated");
        assertThat(bean.getLabel()).isEqualTo("updated");
        assertThat(bean.getNumberBox()).isEqualTo(new NumberBox("42"));
    }

    @Test
    void cloneCreatesNewBeanInstanceAndCopiesReadableWritableProperties() throws CloneNotSupportedException {
        SampleBean bean = new SampleBean();
        BeanMap map = new BeanMap(bean);
        map.put("label", "cloned");
        map.put("numberBox", "99");

        BeanMap clonedMap = (BeanMap) map.clone();
        SampleBean clonedBean = (SampleBean) clonedMap.getBean();

        assertThat(clonedMap).isNotSameAs(map);
        assertThat(clonedBean).isNotSameAs(bean);
        assertThat(clonedMap.get("label")).isEqualTo("cloned");
        assertThat(clonedBean.getNumberBox()).isEqualTo(new NumberBox("99"));
    }

    @Test
    void clearReplacesBeanWithDefaultInstance() {
        SampleBean bean = new SampleBean();
        BeanMap map = new BeanMap(bean);
        map.put("label", "changed");
        map.put("numberBox", "123");

        map.clear();
        SampleBean clearedBean = (SampleBean) map.getBean();

        assertThat(clearedBean).isNotSameAs(bean);
        assertThat(map.get("label")).isEqualTo("initial");
        assertThat(clearedBean.getNumberBox()).isEqualTo(new NumberBox("7"));
    }

    public static final class SampleBean {
        private String label = "initial";
        private NumberBox numberBox = new NumberBox("7");

        public SampleBean() {
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public NumberBox getNumberBox() {
            return numberBox;
        }

        public void setNumberBox(NumberBox numberBox) {
            this.numberBox = numberBox;
        }
    }

    public static final class NumberBox {
        private final String value;

        public NumberBox(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof NumberBox)) {
                return false;
            }
            NumberBox numberBox = (NumberBox) o;
            return value.equals(numberBox.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }
}
