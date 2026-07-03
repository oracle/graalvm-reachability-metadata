/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.Loader;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.mbeans.ContextMBean;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.WebappServiceLoader;
import org.apache.catalina.util.CharsetMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class WebappServiceLoaderTest {

    private static final String SERVICE_RESOURCE = "META-INF/services/" + Object.class.getName();

    @Test
    void loadUsesSystemResourceBranchAndWebappClassLoaderResources(@TempDir Path baseDirectory) throws Exception {
        ServiceResourceClassLoader applicationClassLoader = new ServiceResourceClassLoader(
                getClass().getClassLoader(), SERVICE_RESOURCE,
                writeServiceConfig(baseDirectory.resolve("application-services"), CharsetMapper.class.getName()));
        NullParentClassLoaderContext context = new NullParentClassLoaderContext(applicationClassLoader);
        Tomcat tomcat = createTomcatWithContext(baseDirectory, context);
        try {
            WebappServiceLoader<Object> loader = new WebappServiceLoader<>(context);

            List<Object> services = loader.load(Object.class);

            assertThat(services).hasSize(1);
            assertThat(services.get(0)).isInstanceOf(CharsetMapper.class);
        } finally {
            tomcat.destroy();
        }
    }

    @Test
    void loadUsesContainerClassLoaderResourcesBeforeApplicationResources(@TempDir Path baseDirectory) throws Exception {
        ServiceResourceClassLoader containerClassLoader = new ServiceResourceClassLoader(
                getClass().getClassLoader(), SERVICE_RESOURCE,
                writeServiceConfig(baseDirectory.resolve("container-services"), ContextMBean.class.getName()));
        ServiceResourceClassLoader applicationClassLoader = new ServiceResourceClassLoader(
                getClass().getClassLoader(), SERVICE_RESOURCE,
                writeServiceConfig(baseDirectory.resolve("application-services"), CharsetMapper.class.getName()));
        StandardContext context = new StandardContext();
        context.setParentClassLoader(containerClassLoader);
        context.setLoader(new FixedLoader(applicationClassLoader));
        Tomcat tomcat = createTomcatWithContext(baseDirectory, context);
        try {
            WebappServiceLoader<Object> loader = new WebappServiceLoader<>(context);

            List<Object> services = loader.load(Object.class);

            assertThat(services).hasSize(2);
            assertThat(services.get(0)).isInstanceOf(ContextMBean.class);
            assertThat(services.get(1)).isInstanceOf(CharsetMapper.class);
        } finally {
            tomcat.destroy();
        }
    }

    private static URL writeServiceConfig(Path directory, String providerClassName) throws IOException {
        Path serviceFile = directory.resolve(SERVICE_RESOURCE);
        Files.createDirectories(serviceFile.getParent());
        Files.writeString(serviceFile, providerClassName + System.lineSeparator(), StandardCharsets.UTF_8);
        return serviceFile.toUri().toURL();
    }

    private static Tomcat createTomcatWithContext(Path baseDirectory, StandardContext context) throws IOException {
        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(baseDirectory.resolve("base").toString());
        Path docBase = Files.createDirectories(baseDirectory.resolve("webapp"));
        context.setName("/service-loader");
        context.setPath("/service-loader");
        context.setDocBase(docBase.toAbsolutePath().toString());
        Host host = tomcat.getHost();
        host.addChild(context);
        return tomcat;
    }

    private static final class NullParentClassLoaderContext extends StandardContext {
        NullParentClassLoaderContext(ClassLoader applicationClassLoader) {
            setLoader(new FixedLoader(applicationClassLoader));
        }

        @Override
        public ClassLoader getParentClassLoader() {
            return null;
        }
    }

    private static final class FixedLoader implements Loader {
        private final ClassLoader classLoader;
        private Context context;

        FixedLoader(ClassLoader classLoader) {
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
            return true;
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

    private static final class ServiceResourceClassLoader extends ClassLoader {
        private final String resourceName;
        private final URL serviceConfigUrl;

        ServiceResourceClassLoader(ClassLoader parent, String resourceName, URL serviceConfigUrl) {
            super(parent);
            this.resourceName = resourceName;
            this.serviceConfigUrl = serviceConfigUrl;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (resourceName.equals(name)) {
                return Collections.enumeration(List.of(serviceConfigUrl));
            }
            return super.getResources(name);
        }
    }
}
