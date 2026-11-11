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
import org.springframework.jmx.export.assembler.InterfaceBasedMBeanInfoAssembler;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class InterfaceBasedMBeanInfoAssemblerTest {
    @Test
    void limitsManagementSurfaceToConfiguredInterface() throws Exception {
        InterfaceBasedMBeanInfoAssembler assembler = new InterfaceBasedMBeanInfoAssembler();
        assembler.setManagedInterfaces(QueueView.class);
        assembler.afterPropertiesSet();
        ModelMBeanInfo info = assembler.getMBeanInfo(new QueueMonitor(), "queue");
        assertEquals("Depth", info.getAttributes()[0].getName());
        Set<String> operations = Arrays.stream(info.getOperations())
                .map(MBeanFeatureInfo::getName).collect(Collectors.toSet());
        assertEquals(Set.of("getDepth", "clear"), operations);
    }

    public interface QueueView {
        int getDepth();
        void clear();
    }

    public static class QueueMonitor implements QueueView {
        @Override
        public int getDepth() {
            return 4;
        }

        @Override
        public void clear() {
        }

        public String internalDetails() {
            return "not exposed";
        }
    }
}
