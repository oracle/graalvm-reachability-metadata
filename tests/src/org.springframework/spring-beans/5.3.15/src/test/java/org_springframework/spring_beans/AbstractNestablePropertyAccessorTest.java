/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanWrapperImpl;

public class AbstractNestablePropertyAccessorTest {

    @Test
    public void settingIndexedArrayElementGrowsWritableArrayProperty() {
        ArrayPropertyBean target = new ArrayPropertyBean();
        BeanWrapperImpl wrapper = autoGrowingWrapper(target);

        wrapper.setPropertyValue("names[2]", "spring");

        assertThat(target.getNames()).containsExactly("initial", null, "spring");
    }

    @Test
    public void readingNestedArrayElementGrowsArrayAndInstantiatesComponentValues() {
        NestedArrayBean target = new NestedArrayBean();
        BeanWrapperImpl wrapper = autoGrowingWrapper(target);

        wrapper.setPropertyValue("children[1].name", "leaf");

        assertThat(target.getChildren()).hasSize(2);
        assertThat(target.getChildren()[0]).isNotNull();
        assertThat(target.getChildren()[1].getName()).isEqualTo("leaf");
    }

    @Test
    public void readingIndexedNullArrayPropertyCreatesDefaultSingleDimensionArray() {
        NullSingleDimensionArrayBean target = new NullSingleDimensionArrayBean();
        BeanWrapperImpl wrapper = autoGrowingWrapper(target);

        Object value = wrapper.getPropertyValue("aliases[0]");

        assertThat(value).isEqualTo("");
        assertThat(target.getAliases()).containsExactly("");
    }

    @Test
    public void readingIndexedNullTwoDimensionalArrayPropertyCreatesDefaultNestedArray() {
        NullTwoDimensionalArrayBean target = new NullTwoDimensionalArrayBean();
        BeanWrapperImpl wrapper = autoGrowingWrapper(target);

        Object value = wrapper.getPropertyValue("matrix[0]");

        assertThat(value).isInstanceOf(String[].class);
        assertThat((String[]) value).isEmpty();
        assertThat(target.getMatrix()).hasDimensions(1, 0);
    }

    private static BeanWrapperImpl autoGrowingWrapper(Object target) {
        BeanWrapperImpl wrapper = new BeanWrapperImpl(target);
        wrapper.setAutoGrowNestedPaths(true);
        wrapper.setAutoGrowCollectionLimit(8);
        return wrapper;
    }

    public static class ArrayPropertyBean {
        private String[] names = {"initial"};

        public String[] getNames() {
            return names;
        }

        public void setNames(String[] names) {
            this.names = names;
        }
    }

    public static class NestedArrayBean {
        private Child[] children = new Child[0];

        public Child[] getChildren() {
            return children;
        }

        public void setChildren(Child[] children) {
            this.children = children;
        }
    }

    public static class Child {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class NullSingleDimensionArrayBean {
        private String[] aliases;

        public String[] getAliases() {
            return aliases;
        }

        public void setAliases(String[] aliases) {
            this.aliases = aliases;
        }
    }

    public static class NullTwoDimensionalArrayBean {
        private String[][] matrix;

        public String[][] getMatrix() {
            return matrix;
        }

        public void setMatrix(String[][] matrix) {
            this.matrix = matrix;
        }
    }
}
