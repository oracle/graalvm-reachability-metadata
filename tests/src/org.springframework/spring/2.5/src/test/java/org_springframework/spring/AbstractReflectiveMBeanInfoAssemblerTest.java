/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import java.util.Arrays;

import javax.management.modelmbean.ModelMBeanInfo;

import org.junit.jupiter.api.Test;
import org.springframework.jmx.export.assembler.SimpleReflectiveMBeanInfoAssembler;

import static org.junit.jupiter.api.Assertions.*;

public class AbstractReflectiveMBeanInfoAssemblerTest {
    @Test
    void exposesBeanPropertiesAndPublicOperations() throws Exception {
        SimpleReflectiveMBeanInfoAssembler assembler = new SimpleReflectiveMBeanInfoAssembler();
        ModelMBeanInfo info = assembler.getMBeanInfo(new TestBeans.Person(), "person");
        assertNotNull(info.getAttribute("Name"));
        assertNotNull(info.getAttribute("Age"));
        assertTrue(Arrays.stream(info.getOperations()).anyMatch(operation -> operation.getName().equals("setName")));
    }
}
