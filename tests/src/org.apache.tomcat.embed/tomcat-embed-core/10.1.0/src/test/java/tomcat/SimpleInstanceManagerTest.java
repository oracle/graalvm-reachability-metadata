/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import org.apache.tomcat.SimpleInstanceManager;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleInstanceManagerTest {
    @Test
    void constructsComponentsUsingEachSupportedEntryPoint() throws Exception {
        SimpleInstanceManager manager = new SimpleInstanceManager();
        assertThat(((Component) manager.newInstance(Component.class)).message()).isEqualTo("ready");
        assertThat(((Component) manager.newInstance(Component.class.getName())).message()).isEqualTo("ready");
        assertThat(((Component) manager.newInstance(Component.class.getName(), getClass().getClassLoader()))
                .message()).isEqualTo("ready");
    }

    public static class Component {
        public String message() {
            return "ready";
        }
    }
}
