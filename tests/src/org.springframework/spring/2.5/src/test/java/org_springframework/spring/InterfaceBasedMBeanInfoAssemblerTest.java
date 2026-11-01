/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import javax.management.modelmbean.ModelMBeanInfo;

import org.junit.jupiter.api.Test;
import org.springframework.jmx.export.assembler.InterfaceBasedMBeanInfoAssembler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class InterfaceBasedMBeanInfoAssemblerTest {
    public interface Management {
        int getCount();
    }

    public static class Managed implements Management {
        public int getCount() {
            return 9;
        }

        public String getSecret() {
            return "private management detail";
        }
    }

    @Test
    void exposesOnlyConfiguredManagementInterface() throws Exception {
        InterfaceBasedMBeanInfoAssembler assembler = new InterfaceBasedMBeanInfoAssembler();
        assembler.setManagedInterfaces(new Class<?>[]{Management.class});
        assembler.afterPropertiesSet();
        ModelMBeanInfo info = assembler.getMBeanInfo(new Managed(), "managed");
        assertNotNull(info.getAttribute("Count"));
        assertNull(info.getAttribute("Secret"));
        assertEquals(1, info.getAttributes().length);
    }
}
