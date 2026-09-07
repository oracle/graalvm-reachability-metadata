/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import org.junit.jupiter.api.Test;
import org.springframework.beans.ConfigurablePropertyAccessor;
import org.springframework.beans.PropertyAccessorFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class DirectFieldAccessorInnerFieldPropertyHandlerTest {
    @Test
    void readsAndWritesPrivateField() {
        FieldBean bean = new FieldBean();
        ConfigurablePropertyAccessor accessor = PropertyAccessorFactory.forDirectFieldAccess(bean);

        accessor.setPropertyValue("name", "direct");

        assertThat(accessor.getPropertyValue("name")).isEqualTo("direct");
        assertThat(bean.name()).isEqualTo("direct");
    }

    public static class FieldBean {
        private String name;

        public String name() {
            return this.name;
        }
    }
}
