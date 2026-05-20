/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.beans.PropertyChangeListener;
import java.util.List;

import org.apache.catalina.Context;
import org.apache.catalina.Loader;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardEngine;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.core.StandardService;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.WebappServiceLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class WebappServiceLoaderTest {

    @Test
    void loadsServiceDefinitionsFromClassPath() throws Exception {
        StandardContext context = new StandardContext();
        StandardService service = new StandardService();
        StandardEngine engine = new StandardEngine();
        service.setContainer(engine);
        StandardHost host = new StandardHost();
        host.setParent(engine);
        context.setParent(host);
        context.setLoader(new FixedLoader(getClass().getClassLoader()));
        WebappServiceLoader<Object> loader = new WebappServiceLoader<>(context);

        List<Object> services = loader.load(Object.class);

        assertThat(services).hasAtLeastOneElementOfType(Tomcat.class);
    }

    private static final class FixedLoader implements Loader {

        private final ClassLoader classLoader;
        private Context context;

        private FixedLoader(ClassLoader classLoader) {
            this.classLoader = classLoader;
        }

        @Override
        public void backgroundProcess() {
        }

        @Override
        public ClassLoader getClassLoader() {
            return classLoader;
        }

        @Override
        public Context getContext() {
            return context;
        }

        @Override
        public void setContext(Context context) {
            this.context = context;
        }

        @Override
        public boolean getDelegate() {
            return false;
        }

        @Override
        public void setDelegate(boolean delegate) {
        }

        @Override
        public void addPropertyChangeListener(PropertyChangeListener listener) {
        }

        @Override
        public boolean modified() {
            return false;
        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener listener) {
        }
    }
}
