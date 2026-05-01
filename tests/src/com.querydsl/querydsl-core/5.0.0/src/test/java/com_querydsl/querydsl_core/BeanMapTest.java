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
    void getAndPutUseBeanAccessorsAndConvertConstructorArguments() {
        MutableBean bean = new MutableBean();
        BeanMap map = new BeanMap(bean);

        assertThat(map.get("name")).isEqualTo("default-name");
        assertThat(map.put("name", "updated-name")).isEqualTo("default-name");
        assertThat(bean.getName()).isEqualTo("updated-name");

        assertThat(map.put("wrappedLabel", "converted-label"))
                .isEqualTo(new WrappedLabel("default-label"));
        assertThat(bean.getWrappedLabel()).isEqualTo(new WrappedLabel("converted-label"));
        assertThat(map.get("wrappedLabel")).isEqualTo(new WrappedLabel("converted-label"));
    }

    @Test
    void clearReplacesBeanWithNewDefaultInstance() {
        MutableBean bean = new MutableBean();
        BeanMap map = new BeanMap(bean);

        map.put("name", "changed-before-clear");
        map.put("wrappedLabel", new WrappedLabel("changed-label"));

        map.clear();

        assertThat(map.getBean()).isInstanceOf(MutableBean.class);
        assertThat(map.getBean()).isNotSameAs(bean);
        assertThat(map.get("name")).isEqualTo("default-name");
        assertThat(map.get("wrappedLabel")).isEqualTo(new WrappedLabel("default-label"));
        assertThat(bean.getName()).isEqualTo("changed-before-clear");
    }

    @Test
    void cloneCreatesNewBeanAndCopiesWritableProperties() throws CloneNotSupportedException {
        MutableBean bean = new MutableBean();
        BeanMap map = new BeanMap(bean);
        map.put("name", "source-name");
        map.put("wrappedLabel", new WrappedLabel("source-label"));

        BeanMap clone = (BeanMap) map.clone();

        assertThat(clone).isNotSameAs(map);
        assertThat(clone.getBean()).isInstanceOf(MutableBean.class);
        assertThat(clone.getBean()).isNotSameAs(bean);
        assertThat(clone.get("name")).isEqualTo("source-name");
        assertThat(clone.get("wrappedLabel")).isEqualTo(new WrappedLabel("source-label"));

        map.put("name", "changed-after-clone");

        assertThat(clone.get("name")).isEqualTo("source-name");
        assertThat(bean.getName()).isEqualTo("changed-after-clone");
    }

    public static class MutableBean {

        private String name = "default-name";

        private WrappedLabel wrappedLabel = new WrappedLabel("default-label");

        public MutableBean() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public WrappedLabel getWrappedLabel() {
            return wrappedLabel;
        }

        public void setWrappedLabel(WrappedLabel wrappedLabel) {
            this.wrappedLabel = wrappedLabel;
        }
    }

    public static class WrappedLabel {

        private final String value;

        public WrappedLabel(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof WrappedLabel)) {
                return false;
            }
            WrappedLabel that = (WrappedLabel) other;
            return value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @Override
        public String toString() {
            return value;
        }
    }
}
