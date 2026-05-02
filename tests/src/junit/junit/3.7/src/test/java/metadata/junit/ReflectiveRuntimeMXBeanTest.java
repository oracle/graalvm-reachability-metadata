/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package metadata.junit;

import java.util.List;

import org.junit.internal.management.ManagementFactory;
import org.junit.internal.management.RuntimeMXBean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ReflectiveRuntimeMXBeanTest {
    @Test
    public void readsInputArgumentsThroughManagementFactoryRuntimeBean() {
        RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();

        assertNotNull(runtimeMxBean);
        List<String> inputArguments = runtimeMxBean.getInputArguments();

        assertNotNull(inputArguments);
        assertFalse(inputArguments.contains(null));
    }
}
