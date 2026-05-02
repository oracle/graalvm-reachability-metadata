/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package metadata.junit;

import org.junit.internal.management.ManagementFactory;
import org.junit.internal.management.RuntimeMXBean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ManagementFactoryInnerFactoryHolderTest {
    @Test
    public void getsRuntimeBeanThroughManagementFactoryHolder() {
        RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();

        assertNotNull(runtimeMxBean);
    }
}
