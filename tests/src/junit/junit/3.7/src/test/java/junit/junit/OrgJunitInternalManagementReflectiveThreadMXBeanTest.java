/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit.junit;

import org.junit.internal.management.ManagementFactory;
import org.junit.internal.management.ThreadMXBean;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class OrgJunitInternalManagementReflectiveThreadMXBeanTest {
    @Test
    void managementFactoryThreadBeanQueriesCpuTimeForCurrentThread() {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        boolean supported = threadBean.isThreadCpuTimeSupported();

        if (supported) {
            assertThat(threadBean.getThreadCpuTime(Thread.currentThread().getId())).isGreaterThanOrEqualTo(-1L);
        } else {
            assertThatThrownBy(() -> threadBean.getThreadCpuTime(Thread.currentThread().getId()))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
