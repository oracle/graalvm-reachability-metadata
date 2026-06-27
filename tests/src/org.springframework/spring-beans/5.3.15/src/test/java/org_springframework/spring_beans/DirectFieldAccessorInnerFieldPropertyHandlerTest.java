/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import org.junit.jupiter.api.Test;
import org.springframework.beans.DirectFieldAccessor;

import static org.assertj.core.api.Assertions.assertThat;

public class DirectFieldAccessorInnerFieldPropertyHandlerTest {

    @Test
    void directFieldAccessorReadsAndWritesPrivateField() {
        FieldBackedBean bean = new FieldBackedBean();
        DirectFieldAccessor accessor = new DirectFieldAccessor(bean);

        accessor.setPropertyValue("name", "spring");
        Object value = accessor.getPropertyValue("name");

        assertThat(value).isEqualTo("spring");
        assertThat(bean.name).isEqualTo("spring");
    }

    private static class FieldBackedBean {
        private String name;
    }
}
