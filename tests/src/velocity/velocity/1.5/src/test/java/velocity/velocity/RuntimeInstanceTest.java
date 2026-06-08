/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.commons.collections.ExtendedProperties;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.RuntimeInstance;
import org.apache.velocity.runtime.resource.ResourceManager;
import org.junit.jupiter.api.Test;

public class RuntimeInstanceTest {
    @Test
    public void rejectsConfiguredResourceManagerClassWithWrongType() {
        final ExtendedProperties configuration = new ExtendedProperties();
        configuration.setProperty(
                RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS,
                "org.apache.velocity.runtime.log.NullLogChute");
        configuration.setProperty(
                RuntimeConstants.RESOURCE_MANAGER_CLASS,
                Object.class.getName());

        final RuntimeInstance runtime = new RuntimeInstance();
        runtime.setConfiguration(configuration);

        assertThatThrownBy(runtime::init)
                .isInstanceOf(Exception.class)
                .hasMessageContaining(Object.class.getName())
                .hasMessageContaining(ResourceManager.class.getName());
    }
}
