/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_yaml.snakeyaml;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

public class ConstructorConstructSequenceTest {

    @Test
    void constructsImmutableObjectFromSequenceUsingSingleDeclaredConstructor() {
        SingleDeclaredConstructorSequenceBean bean = new Yaml().loadAs(
                """
                - Example
                - 7
                """,
                SingleDeclaredConstructorSequenceBean.class);

        assertThat(bean.getName()).isEqualTo("Example");
        assertThat(bean.getQuantity()).isEqualTo(7);
    }

    @Test
    void constructsImmutableObjectFromSequenceUsingMatchingConstructorAmongMultipleChoices() {
        MultipleDeclaredConstructorsSequenceBean bean = new Yaml().loadAs(
                """
                - 12
                - true
                """,
                MultipleDeclaredConstructorsSequenceBean.class);

        assertThat(bean.getConstructionPath()).isEqualTo("primitive");
        assertThat(bean.getQuantity()).isEqualTo(12);
        assertThat(bean.isEnabled()).isTrue();
    }

    public static final class SingleDeclaredConstructorSequenceBean {
        private final String name;
        private final int quantity;

        private SingleDeclaredConstructorSequenceBean(String name, Integer quantity) {
            this.name = name;
            this.quantity = quantity;
        }

        public String getName() {
            return name;
        }

        public int getQuantity() {
            return quantity;
        }
    }

    public static final class MultipleDeclaredConstructorsSequenceBean {
        private final String constructionPath;
        private final int quantity;
        private final boolean enabled;

        private MultipleDeclaredConstructorsSequenceBean(String label, String state) {
            this.constructionPath = label + ":" + state;
            this.quantity = -1;
            this.enabled = false;
        }

        private MultipleDeclaredConstructorsSequenceBean(int quantity, boolean enabled) {
            this.constructionPath = "primitive";
            this.quantity = quantity;
            this.enabled = enabled;
        }

        public String getConstructionPath() {
            return constructionPath;
        }

        public int getQuantity() {
            return quantity;
        }

        public boolean isEnabled() {
            return enabled;
        }
    }
}
