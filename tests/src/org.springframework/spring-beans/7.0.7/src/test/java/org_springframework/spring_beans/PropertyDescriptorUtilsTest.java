/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import java.beans.PropertyDescriptor;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PropertyDescriptorUtilsTest {
    @Test
    void describesBooleanAndStringBeanProperties() throws Exception {
        PropertyDescriptor enabled = Arrays.stream(BeanUtils.getPropertyDescriptors(Feature.class))
                .filter(property -> property.getName().equals("enabled")).findFirst().orElseThrow();
        assertEquals(boolean.class, enabled.getPropertyType());
        assertEquals("isEnabled", enabled.getReadMethod().getName());
        assertEquals("setEnabled", enabled.getWriteMethod().getName());
    }

    public static class Feature {
        private boolean enabled;

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
