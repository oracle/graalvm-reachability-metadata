/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class CachedIntrospectionResultsTest {
    @Test
    void discoversPlainAccessorBackedBySameNamedField() {
        BeanWrapper wrapper = PropertyAccessorFactory.forBeanPropertyAccess(new PlainAccessorBean());

        assertThat(wrapper.isReadableProperty("status")).isTrue();
        assertThat(wrapper.getPropertyValue("status")).isEqualTo("ready");
    }

    public static class PlainAccessorBean {
        private final String status = "ready";

        public String status() {
            return this.status;
        }
    }
}
