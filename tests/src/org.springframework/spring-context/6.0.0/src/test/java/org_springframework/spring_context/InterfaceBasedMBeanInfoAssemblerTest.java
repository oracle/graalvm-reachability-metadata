/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanOperationInfo;
import javax.management.modelmbean.ModelMBeanInfo;

import org.junit.jupiter.api.Test;

import org.springframework.jmx.export.assembler.InterfaceBasedMBeanInfoAssembler;

public class InterfaceBasedMBeanInfoAssemblerTest {

    @Test
    void exposesOnlyMethodsDeclaredOnManagedInterface() throws Exception {
        final InterfaceBasedMBeanInfoAssembler assembler = new InterfaceBasedMBeanInfoAssembler();
        assembler.setManagedInterfaces(ManagedServiceOperations.class);

        final ModelMBeanInfo info = assembler.getMBeanInfo(new ManagedService(), "managedService");

        assertAttributePresent(info, "Status");
        assertOperationPresent(info, "reset");
        assertOperationAbsent(info, "internalOperation");
    }

    private static void assertAttributePresent(ModelMBeanInfo info, String name) {
        final boolean present = Arrays.stream(info.getAttributes())
                .map(MBeanAttributeInfo::getName)
                .anyMatch(name::equals);

        assertTrue(present, () -> "Expected attribute " + name + " in assembled MBean metadata");
    }

    private static void assertOperationPresent(ModelMBeanInfo info, String name) {
        final boolean present = Arrays.stream(info.getOperations())
                .map(MBeanOperationInfo::getName)
                .anyMatch(name::equals);

        assertTrue(present, () -> "Expected operation " + name + " in assembled MBean metadata");
    }

    private static void assertOperationAbsent(ModelMBeanInfo info, String name) {
        final boolean present = Arrays.stream(info.getOperations())
                .map(MBeanOperationInfo::getName)
                .anyMatch(name::equals);

        assertFalse(present, () -> "Did not expect operation " + name + " in assembled MBean metadata");
    }

    public interface ManagedServiceOperations {
        String getStatus();

        void setStatus(String status);

        void reset();
    }

    public static final class ManagedService implements ManagedServiceOperations {
        private String status = "started";

        @Override
        public String getStatus() {
            return this.status;
        }

        @Override
        public void setStatus(String status) {
            this.status = status;
        }

        @Override
        public void reset() {
            this.status = "reset";
        }

        public void internalOperation() {
            this.status = "internal";
        }
    }
}
