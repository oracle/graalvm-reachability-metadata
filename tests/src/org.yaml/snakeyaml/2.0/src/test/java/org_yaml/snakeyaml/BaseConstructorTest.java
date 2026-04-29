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

public class BaseConstructorTest {

    @Test
    void constructsTypedStringArrayFromSequence() {
        String[] values = new Yaml().loadAs(
                """
                - alpha
                - beta
                - gamma
                """,
                String[].class);

        assertThat(values).containsExactly("alpha", "beta", "gamma");
    }

    @Test
    void constructsBeanUsingPrivateNoArgConstructor() {
        String yaml = """
                name: Example
                quantity: 7
                """;

        PrivateDefaultConstructorBean bean =
                new Yaml().loadAs(yaml, PrivateDefaultConstructorBean.class);

        assertThat(bean.wasCreatedByConstructor()).isTrue();
        assertThat(bean.getName()).isEqualTo("Example");
        assertThat(bean.getQuantity()).isEqualTo(7);
    }

    public static final class PrivateDefaultConstructorBean {
        private final boolean createdByConstructor;
        private String name;
        private int quantity;

        private PrivateDefaultConstructorBean() {
            this.createdByConstructor = true;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        public boolean wasCreatedByConstructor() {
            return createdByConstructor;
        }
    }
}
