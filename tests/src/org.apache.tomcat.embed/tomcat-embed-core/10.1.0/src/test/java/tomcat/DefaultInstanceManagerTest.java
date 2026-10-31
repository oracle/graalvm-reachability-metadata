/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.io.File;
import java.util.Map;

import org.apache.catalina.core.DefaultInstanceManager;
import org.apache.naming.NamingContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefaultInstanceManagerTest {
    @TempDir
    File base;

    @Test
    void injectsResourcesAndRunsLifecycleCallbacks() throws Exception {
        try (EmbeddedApplication application = new EmbeddedApplication(base)) {
            application.context.addPostConstructMethod(Component.class.getName(), "initialize");
            application.context.addPreDestroyMethod(Component.class.getName(), "release");
            application.start();
            NamingContext naming = new NamingContext(null, "components");
            try {
                naming.bind("fieldValue", "catalog");
                naming.bind("setterValue", "orders");
                Map<String, Map<String, String>> injections = Map.of(Component.class.getName(),
                        Map.of("field", "fieldValue", "name", "setterValue"));
                DefaultInstanceManager manager = new DefaultInstanceManager(naming, injections,
                        application.context, getClass().getClassLoader());
                Component first = (Component) manager.newInstance(Component.class);
                Component second = (Component) manager.newInstance(Component.class.getName());
                Component third = (Component) manager.newInstance(Component.class.getName(), getClass().getClassLoader());
                for (Component component : new Component[] {first, second, third}) {
                    assertEquals("catalog:orders", component.initializedValue);
                    manager.destroyInstance(component);
                    assertTrue(component.released);
                }
            } finally {
                naming.close();
            }
        }
    }

    public static class Component {
        private String field;
        private String name;
        private String initializedValue;
        private boolean released;

        public void setName(String name) {
            this.name = name;
        }

        private void initialize() {
            initializedValue = field + ":" + name;
        }

        private void release() {
            released = true;
        }
    }
}
