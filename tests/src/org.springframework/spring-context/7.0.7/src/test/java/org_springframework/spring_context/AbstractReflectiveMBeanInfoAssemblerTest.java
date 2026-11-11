/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import javax.management.MBeanFeatureInfo;
import javax.management.modelmbean.ModelMBeanInfo;

import org.junit.jupiter.api.Test;
import org.springframework.jmx.export.assembler.SimpleReflectiveMBeanInfoAssembler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AbstractReflectiveMBeanInfoAssemblerTest {
    @Test
    void exposesBeanPropertiesAndPublicOperations() throws Exception {
        SimpleReflectiveMBeanInfoAssembler assembler = new SimpleReflectiveMBeanInfoAssembler();
        ModelMBeanInfo info = assembler.getMBeanInfo(new QueueMonitor(), "queue");
        assertEquals("Depth", info.getAttributes()[0].getName());
        assertTrue(info.getAttributes()[0].isReadable());
        Set<String> operations = Arrays.stream(info.getOperations())
                .map(MBeanFeatureInfo::getName).collect(Collectors.toSet());
        assertEquals(Set.of("getDepth", "clear"), operations);
    }

    public static class QueueMonitor {
        public int getDepth() {
            return 3;
        }

        public void clear() {
        }
    }
}
