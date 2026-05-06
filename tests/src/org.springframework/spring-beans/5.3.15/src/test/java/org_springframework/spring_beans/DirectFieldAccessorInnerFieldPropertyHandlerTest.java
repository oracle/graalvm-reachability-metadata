/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.DirectFieldAccessor;

public class DirectFieldAccessorInnerFieldPropertyHandlerTest {

    @Test
    public void directFieldAccessorReadsAndWritesPrivateField() {
        FieldBackedBean target = new FieldBackedBean("initial");
        DirectFieldAccessor accessor = new DirectFieldAccessor(target);

        accessor.setPropertyValue("name", "spring");
        Object value = accessor.getPropertyValue("name");

        assertThat(value).isEqualTo("spring");
        assertThat(target.name).isEqualTo("spring");
    }

    public static class FieldBackedBean {
        private String name;

        public FieldBackedBean(String name) {
            this.name = name;
        }
    }
}
