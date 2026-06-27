/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanWrapperImpl;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractNestablePropertyAccessorTest {

    @Test
    void setIndexedArrayPropertyGrowsArrayBeforeSettingValue() {
        ArrayBackedBean bean = new ArrayBackedBean();
        bean.setTags(new String[] {"spring"});
        BeanWrapperImpl wrapper = new BeanWrapperImpl(bean);

        wrapper.setPropertyValue("tags[2]", "beans");

        assertThat(bean.getTags()).containsExactly("spring", null, "beans");
    }

    @Test
    void getNestedArrayElementAutoGrowsArrayAndElementBean() {
        ArrayBackedBean bean = new ArrayBackedBean();
        bean.setChildren(new ChildBean[0]);
        BeanWrapperImpl wrapper = new BeanWrapperImpl(bean);
        wrapper.setAutoGrowNestedPaths(true);

        Object value = wrapper.getPropertyValue("children[1].name");

        assertThat(value).isNull();
        assertThat(bean.getChildren()).hasSize(2);
        assertThat(bean.getChildren()[0]).isNotNull();
        assertThat(bean.getChildren()[1]).isNotNull();
    }

    @Test
    void getIndexedPropertyAutoInitializesOneDimensionalArray() {
        ArrayBackedBean bean = new ArrayBackedBean();
        BeanWrapperImpl wrapper = new BeanWrapperImpl(bean);
        wrapper.setAutoGrowNestedPaths(true);

        Object value = wrapper.getPropertyValue("tags[0]");

        assertThat(value).isEqualTo("");
        assertThat(bean.getTags()).containsExactly("");
    }

    @Test
    void getIndexedPropertyAutoInitializesTwoDimensionalArray() {
        ArrayBackedBean bean = new ArrayBackedBean();
        BeanWrapperImpl wrapper = new BeanWrapperImpl(bean);
        wrapper.setAutoGrowNestedPaths(true);

        Object value = wrapper.getPropertyValue("matrix[0]");

        assertThat(value).isInstanceOf(String[].class);
        assertThat((String[]) value).isEmpty();
        assertThat(bean.getMatrix().length).isEqualTo(1);
        assertThat(bean.getMatrix()[0]).isEmpty();
    }

    public static class ArrayBackedBean {
        private String[] tags;
        private String[][] matrix;
        private ChildBean[] children;

        public String[] getTags() {
            return tags;
        }

        public void setTags(String[] tags) {
            this.tags = tags;
        }

        public String[][] getMatrix() {
            return matrix;
        }

        public void setMatrix(String[][] matrix) {
            this.matrix = matrix;
        }

        public ChildBean[] getChildren() {
            return children;
        }

        public void setChildren(ChildBean[] children) {
            this.children = children;
        }
    }

    public static class ChildBean {
        private String name;

        public ChildBean() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
