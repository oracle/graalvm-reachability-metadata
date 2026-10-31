/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.apache.catalina.core.DefaultInstanceManager;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardWrapper;
import org.apache.catalina.startup.Tomcat;
import org.apache.naming.NamingContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultInstanceManagerTest {
    @TempDir
    Path directory;

    @Test
    void constructsInjectsAndDestroysApplicationComponents() throws Exception {
        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(directory.resolve("server").toString());
        StandardContext context = (StandardContext) tomcat.addContext("",
                Files.createDirectory(directory.resolve("webapp")).toString());
        context.addPostConstructMethod(ApplicationComponent.class.getName(), "initialize");
        context.addPreDestroyMethod(ApplicationComponent.class.getName(), "close");
        NamingContext naming = new NamingContext(null, "components");
        naming.bind("fieldResource", "field-value");
        naming.bind("setterResource", "setter-value");
        try {
            tomcat.start();
            DefaultInstanceManager manager = new DefaultInstanceManager(naming,
                    Map.of(ApplicationComponent.class.getName(),
                            Map.of("fieldValue", "fieldResource", "setterValue", "setterResource")),
                    context, ApplicationComponent.class.getClassLoader());
            ApplicationComponent typed = (ApplicationComponent) manager.newInstance(ApplicationComponent.class);
            ApplicationComponent named = (ApplicationComponent) manager.newInstance(ApplicationComponent.class.getName());
            ApplicationComponent loaded = (ApplicationComponent) manager.newInstance(
                    ApplicationComponent.class.getName(), ApplicationComponent.class.getClassLoader());
            for (ApplicationComponent component : new ApplicationComponent[] {typed, named, loaded}) {
                assertThat(component.fieldValue).isEqualTo("field-value");
                assertThat(component.setterValue).isEqualTo("setter-value");
                assertThat(component.initialized).isTrue();
                manager.destroyInstance(component);
                assertThat(component.closed).isTrue();
            }
            StandardWrapper wrapper = (StandardWrapper) manager.newInstance(StandardWrapper.class.getName());
            wrapper.setName("managed-wrapper");
            assertThat(wrapper.getName()).isEqualTo("managed-wrapper");
            manager.destroyInstance(wrapper);
        } finally {
            naming.close();
            tomcat.stop();
            tomcat.destroy();
        }
    }

    public static class ApplicationComponent {
        private String fieldValue;
        private String setterValue;
        private boolean initialized;
        private boolean closed;

        public void setSetterValue(String value) {
            setterValue = value;
        }

        public void initialize() {
            initialized = fieldValue != null && setterValue != null;
        }

        public void close() {
            closed = true;
        }
    }
}
