/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package metadata.junit;

import org.junit.internal.management.ManagementFactory;
import org.junit.internal.management.ThreadMXBean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReflectiveThreadMXBeanTest {
    @Test
    public void readsThreadCpuTimeThroughManagementFactoryThreadBean() {
        ThreadMXBean threadMxBean = ManagementFactory.getThreadMXBean();
        long currentThreadId = Thread.currentThread().getId();

        assertNotNull(threadMxBean);
        boolean threadCpuTimeSupported = threadMxBean.isThreadCpuTimeSupported();

        try {
            long threadCpuTime = threadMxBean.getThreadCpuTime(currentThreadId);

            assertTrue(threadCpuTime >= -1L);
        } catch (UnsupportedOperationException exception) {
            assertFalse(threadCpuTimeSupported);
        }
    }
}
