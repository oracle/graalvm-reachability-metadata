/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.RuntimeInstance;
import org.apache.velocity.runtime.RuntimeServices;
import org.apache.velocity.runtime.log.LogChute;
import org.apache.velocity.runtime.resource.ResourceManager;
import org.junit.jupiter.api.Test;

public class RuntimeInstanceTest {
    @Test
    void reportsConfiguredResourceManagerClassThatDoesNotImplementResourceManager() throws Exception {
        RuntimeInstance runtime = runtimeWithInitializedConfiguration(
            RuntimeConstants.RESOURCE_MANAGER_CLASS + " = " + String.class.getName() + "\n");

        assertThatThrownBy(runtime::init)
            .isInstanceOf(Exception.class)
            .hasMessageContaining(String.class.getName())
            .hasMessageContaining(ResourceManager.class.getName());
    }

    private static RuntimeInstance runtimeWithInitializedConfiguration(String properties) throws Exception {
        RuntimeInstance runtime = new RuntimeInstance();
        byte[] bytes = properties.getBytes(StandardCharsets.ISO_8859_1);
        runtime.getConfiguration().load(new ByteArrayInputStream(bytes));
        runtime.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM, new SilentLogChute());
        return runtime;
    }

    private static final class SilentLogChute implements LogChute {
        @Override
        public void init(RuntimeServices runtimeServices) {
        }

        @Override
        public void log(int level, String message) {
        }

        @Override
        public void log(int level, String message, Throwable throwable) {
        }

        @Override
        public boolean isLevelEnabled(int level) {
            return false;
        }
    }
}
