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

public class CachedIntrospectionResultsTest {

    @Test
    public void beanWrapperRecognizesFieldBackedPlainAccessor() {
        PlainAccessorBean bean = new PlainAccessorBean("Spring");
        BeanWrapperImpl wrapper = new BeanWrapperImpl(bean);

        Object value = wrapper.getPropertyValue("displayName");

        assertThat(value).isEqualTo("Spring");
        assertThat(wrapper.getPropertyDescriptor("displayName").getReadMethod().getName()).isEqualTo("displayName");
    }

    public static class PlainAccessorBean {
        private final String displayName;

        public PlainAccessorBean(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }
}
