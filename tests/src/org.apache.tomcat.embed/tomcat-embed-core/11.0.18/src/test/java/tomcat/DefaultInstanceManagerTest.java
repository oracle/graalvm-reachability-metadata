/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.beans.PropertyChangeListener;
import java.util.Map;

import org.apache.catalina.Context;
import org.apache.catalina.Loader;
import org.apache.catalina.core.DefaultInstanceManager;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardWrapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultInstanceManagerTest {

    @Test
    void instantiatesLibraryClassThroughAllPublicPaths() throws Exception {
        StandardContext context = new StandardContext();
        context.setLoader(new FixedLoader(getClass().getClassLoader()));
        DefaultInstanceManager manager = new DefaultInstanceManager(
                null, Map.of(), context, getClass().getClassLoader());
        String className = StandardWrapper.class.getName();

        assertThat(manager.newInstance(StandardWrapper.class)).isInstanceOf(StandardWrapper.class);
        assertThat(manager.newInstance(className)).isInstanceOf(StandardWrapper.class);
        assertThat(manager.newInstance(className, getClass().getClassLoader())).isInstanceOf(StandardWrapper.class);
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
