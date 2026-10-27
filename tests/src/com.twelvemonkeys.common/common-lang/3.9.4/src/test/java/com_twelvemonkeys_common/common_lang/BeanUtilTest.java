/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_twelvemonkeys_common.common_lang;

import com.twelvemonkeys.lang.BeanUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanUtilTest {
    @Test
    void readsAndWritesBeanProperties() throws Exception {
        MutableBean bean = new MutableBean();
        bean.setChild(new ChildBean("initial"));

        assertThat(BeanUtil.getPropertyValue(bean, "child.name")).isEqualTo("initial");

        BeanUtil.setPropertyValue(bean, "text", "updated");
        BeanUtil.setPropertyValue(bean, "count", Integer.valueOf(7));
        BeanUtil.setPropertyValue(bean, "parent", new ChildValue("child"));
        BeanUtil.setPropertyValue(bean, "converted", "42");

        assertThat(bean.getText()).isEqualTo("updated");
        assertThat(bean.getCount()).isEqualTo(7);
        assertThat(bean.getParent().getValue()).isEqualTo("child");
        assertThat(bean.getConverted()).isEqualTo(42);
    }

    @Test
    void createsObjectsAndInvokesStaticFactories() throws Exception {
        Constructed created = BeanUtil.createInstance(Constructed.class, "constructor");
        Object invoked = BeanUtil.invokeStaticMethod(StaticFactory.class, "from", "factory");

        assertThat(created.getValue()).isEqualTo("constructor");
        assertThat(invoked).isEqualTo("FACTORY");
    }

    public static class MutableBean {
        private ChildBean child;
        private String text;
        private int count;
        private ParentValue parent;
        private int converted;

        public ChildBean getChild() {
            return child;
        }

        public void setChild(ChildBean child) {
            this.child = child;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public ParentValue getParent() {
            return parent;
        }

        public void setParent(ParentValue parent) {
            this.parent = parent;
        }

        public int getConverted() {
            return converted;
        }

        public void setConverted(int converted) {
            this.converted = converted;
        }
    }

    public static class ChildBean {
        private final String name;

        public ChildBean(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public static class ParentValue {
        private final String value;

        public ParentValue(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public static class ChildValue extends ParentValue {
        public ChildValue(String value) {
            super(value);
        }
    }

    public static class Constructed {
        private final String value;

        public Constructed(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public static class StaticFactory {
        public static String from(String value) {
            return value.toUpperCase();
        }
    }
}
