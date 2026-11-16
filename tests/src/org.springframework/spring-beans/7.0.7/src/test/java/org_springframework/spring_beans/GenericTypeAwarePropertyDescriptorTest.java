/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanWrapperImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GenericTypeAwarePropertyDescriptorTest {
    @Test
    void resolvesWriteOnlyPropertyTypeAndConvertsInput() {
        Options options = new Options();
        BeanWrapperImpl wrapper = new BeanWrapperImpl(options);
        assertEquals(Integer.class, wrapper.getPropertyType("limit"));
        wrapper.setPropertyValue("limit", "12");
        assertEquals(12, options.limit);
    }

    public static class Options {
        private Integer limit;

        public void setLimit(Integer limit) {
            this.limit = limit;
        }
    }
}
