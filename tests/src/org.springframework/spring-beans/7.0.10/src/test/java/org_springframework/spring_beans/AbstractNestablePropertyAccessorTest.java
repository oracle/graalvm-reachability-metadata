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
    void growsAndCreatesNestedArrayValues() {
        ArrayBean bean = new ArrayBean();
        BeanWrapperImpl wrapper = new BeanWrapperImpl(bean);
        wrapper.setAutoGrowNestedPaths(true);
        wrapper.setAutoGrowCollectionLimit(10);

        wrapper.setPropertyValue("labels[2]", "grown");
        assertThat(bean.getLabels()).containsExactly(null, null, "grown");

        assertThat(wrapper.getPropertyValue("children[1].name")).isNull();
        assertThat(bean.getChildren()).hasSize(2).doesNotContainNull();

        assertThat(wrapper.getPropertyValue("optionalLabels[0]")).isEqualTo("");
        assertThat(bean.getOptionalLabels()).containsExactly("");

        assertThat(wrapper.getPropertyValue("matrix[0][0]")).isEqualTo("");
        assertThat(bean.getMatrix()).hasDimensions(1, 1);
    }

    public static class ArrayBean {
        private String[] labels = new String[0];
        private Child[] children = new Child[0];
        private String[] optionalLabels;
        private String[][] matrix;

        public String[] getLabels() {
            return this.labels;
        }

        public void setLabels(String[] labels) {
            this.labels = labels;
        }

        public Child[] getChildren() {
            return this.children;
        }

        public void setChildren(Child[] children) {
            this.children = children;
        }

        public String[] getOptionalLabels() {
            return this.optionalLabels;
        }

        public void setOptionalLabels(String[] optionalLabels) {
            this.optionalLabels = optionalLabels;
        }

        public String[][] getMatrix() {
            return this.matrix;
        }

        public void setMatrix(String[][] matrix) {
            this.matrix = matrix;
        }
    }

    public static class Child {
        private String name;

        public Child() {
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
