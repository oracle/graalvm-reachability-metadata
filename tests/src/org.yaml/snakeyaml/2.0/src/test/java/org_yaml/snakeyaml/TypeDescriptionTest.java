/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_yaml.snakeyaml;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

public class TypeDescriptionTest {

    @Test
    void constructsRootBeanUsingConfiguredImplementationDeclaredConstructor() {
        TypeDescription rootType =
                new TypeDescription(AbstractConfiguredBean.class, DeclaredConstructorBean.class);
        Yaml yaml = new Yaml(new Constructor(rootType));

        AbstractConfiguredBean bean = yaml.load(
                """
                name: Example
                quantity: 7
                """);

        assertThat(bean).isInstanceOf(DeclaredConstructorBean.class);
        assertThat(bean.getName()).isEqualTo("Example");
        assertThat(bean.getQuantity()).isEqualTo(7);
        assertThat(((DeclaredConstructorBean) bean).wasCreatedByDeclaredConstructor()).isTrue();
    }

    public abstract static class AbstractConfiguredBean {
        private String name;
        private int quantity;

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
    }

    public static final class DeclaredConstructorBean extends AbstractConfiguredBean {
        private final boolean createdByDeclaredConstructor;

        private DeclaredConstructorBean() {
            this.createdByDeclaredConstructor = true;
        }

        public boolean wasCreatedByDeclaredConstructor() {
            return createdByDeclaredConstructor;
        }
    }
}
