/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import javax.management.Descriptor;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanOperationInfo;
import javax.management.modelmbean.ModelMBeanInfo;
import javax.management.modelmbean.ModelMBeanOperationInfo;

import org.junit.jupiter.api.Test;

import org.springframework.jmx.export.assembler.SimpleReflectiveMBeanInfoAssembler;

public class AbstractReflectiveMBeanInfoAssemblerTest {

    @Test
    void buildsOperationMetadataFromPublicBeanMethods() throws Exception {
        final SimpleReflectiveMBeanInfoAssembler assembler = new SimpleReflectiveMBeanInfoAssembler();
        assembler.setDefaultCurrencyTimeLimit(15);

        final ModelMBeanInfo info = assembler.getMBeanInfo(new ManagedCounter(), "counter");

        assertAttributePresent(info, "Count");
        assertOperationDescriptor(info, "getCount", "getter", "4", null);
        assertOperationDescriptor(info, "setCount", "setter", "4", null);
        assertOperationDescriptor(info, "reset", "operation", null, "15");
    }

    private static void assertAttributePresent(ModelMBeanInfo info, String name) {
        final boolean present = Arrays.stream(info.getAttributes())
                .map(MBeanAttributeInfo::getName)
                .anyMatch(name::equals);

        assertTrue(present, () -> "Expected attribute " + name + " in assembled MBean metadata");
    }

    private static void assertOperationDescriptor(
            ModelMBeanInfo info,
            String operationName,
            String expectedRole,
            String expectedVisibility,
            String expectedCurrencyTimeLimit) {

        final ModelMBeanOperationInfo operationInfo = Arrays.stream(info.getOperations())
                .filter(operation -> operationName.equals(operation.getName()))
                .map(AbstractReflectiveMBeanInfoAssemblerTest::asModelOperationInfo)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing operation " + operationName));
        final Descriptor descriptor = operationInfo.getDescriptor();

        assertEquals(expectedRole, descriptor.getFieldValue("role"));
        if (expectedVisibility != null) {
            assertEquals(expectedVisibility, descriptor.getFieldValue("visibility").toString());
        }
        assertEquals(expectedCurrencyTimeLimit, descriptor.getFieldValue("currencyTimeLimit"));
        assertNotNull(operationInfo.getDescription());
    }

    private static ModelMBeanOperationInfo asModelOperationInfo(MBeanOperationInfo operationInfo) {
        assertTrue(operationInfo instanceof ModelMBeanOperationInfo);
        return (ModelMBeanOperationInfo) operationInfo;
    }

    public static final class ManagedCounter {
        private int count;

        public int getCount() {
            return this.count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public void reset() {
            this.count = 0;
        }
    }
}
