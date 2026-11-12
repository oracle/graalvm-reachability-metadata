/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.util.Map;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.apache.catalina.core.DefaultInstanceManager;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.loader.WebappLoader;
import org.apache.naming.NamingContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultInstanceManagerTest {
    @Test
    void injectsResourcesAndInvokesLifecycleCallbacks() throws Exception {
        StandardContext context = new StandardContext();
        context.setLoader(new WebappLoader() {
            @Override
            public ClassLoader getClassLoader() {
                return Component.class.getClassLoader();
            }
        });
        NamingContext naming = new NamingContext(null, "injections");
        naming.bind("greeting", "hello");
        DefaultInstanceManager manager = new DefaultInstanceManager(naming, Map.of(), context,
                getClass().getClassLoader());
        Component byType = (Component) manager.newInstance(Component.class);
        Component byName = (Component) manager.newInstance(Component.class.getName());
        Component byLoader = (Component) manager.newInstance(Component.class.getName(), getClass().getClassLoader());
        for (Component component : new Component[] {byType, byName, byLoader}) {
            assertThat(component.field).isEqualTo("hello");
            assertThat(component.message).isEqualTo("hello");
            assertThat(component.started).isTrue();
            manager.destroyInstance(component);
            assertThat(component.stopped).isTrue();
        }
        naming.close();
    }

    public static class Component {
        @Resource(name = "greeting")
        private String field;
        private String message;
        private boolean started;
        private boolean stopped;

        @Resource(name = "greeting")
        public void setMessage(String message) {
            this.message = message;
        }

        @PostConstruct
        private void start() {
            started = true;
        }

        @PreDestroy
        private void stop() {
            stopped = true;
        }
    }
}
