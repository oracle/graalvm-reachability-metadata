/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import java.beans.BeanInfo;
import java.beans.PropertyDescriptor;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.beans.ExtendedBeanInfoFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ExtendedBeanInfoFactoryTest {
    @Test
    void recognizesFluentSetters() throws Exception {
        BeanInfo info = new ExtendedBeanInfoFactory().getBeanInfo(FluentOptions.class);
        PropertyDescriptor property = Arrays.stream(info.getPropertyDescriptors())
                .filter(candidate -> candidate.getName().equals("name")).findFirst().orElseThrow();
        assertNotNull(property.getWriteMethod());
        assertEquals("setName", property.getWriteMethod().getName());
        assertEquals(FluentOptions.class, property.getWriteMethod().getReturnType());
        assertEquals(String.class, property.getPropertyType());
    }

    public static class FluentOptions {
        private String name;

        public String getName() {
            return this.name;
        }

        public FluentOptions setName(String name) {
            this.name = name;
            return this;
        }
    }
}
