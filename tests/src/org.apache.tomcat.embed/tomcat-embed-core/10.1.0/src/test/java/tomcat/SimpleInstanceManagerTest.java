/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import org.apache.tomcat.SimpleInstanceManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class SimpleInstanceManagerTest {
    @Test
    void constructsApplicationComponentsThroughAllOverloads() throws Exception {
        SimpleInstanceManager manager = new SimpleInstanceManager();
        Component first = (Component) manager.newInstance(Component.class);
        Component second = (Component) manager.newInstance(Component.class.getName());
        Component third = (Component) manager.newInstance(Component.class.getName(), getClass().getClassLoader());
        assertEquals("ready", first.message());
        assertEquals("ready", second.message());
        assertEquals("ready", third.message());
        assertNotSame(first, second);
        assertNotSame(second, third);
    }

    public static class Component {
        public String message() {
            return "ready";
        }
    }
}
