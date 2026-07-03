/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.beans.PropertyChangeListener;

import javax.naming.NamingException;

import org.apache.catalina.Context;
import org.apache.catalina.Loader;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardServer;
import org.apache.catalina.deploy.NamingResourcesImpl;
import org.apache.naming.NamingContext;
import org.apache.tomcat.util.descriptor.web.ContextEnvironment;
import org.apache.tomcat.util.descriptor.web.ContextResource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NamingResourcesImplTest {

    @Test
    void addEnvironmentInfersTypeFromInjectionSetter() {
        NamingResourcesImpl namingResources = namingResourcesForCurrentClassLoader();
        ContextEnvironment environment = environmentWithInjectionTarget("setterResource", SetterInjectionTarget.class,
                "value");

        namingResources.addEnvironment(environment);

        assertThat(namingResources.findEnvironment("setterResource")).isSameAs(environment);
        assertThat(environment.getType()).isEqualTo(String.class.getCanonicalName());
    }

    @Test
    void addEnvironmentInfersTypeFromInjectionFieldWhenSetterIsAbsent() {
        NamingResourcesImpl namingResources = namingResourcesForCurrentClassLoader();
        ContextEnvironment environment = environmentWithInjectionTarget("fieldResource", FieldInjectionTarget.class,
                "value");

        namingResources.addEnvironment(environment);

        assertThat(namingResources.findEnvironment("fieldResource")).isSameAs(environment);
        assertThat(environment.getType()).isEqualTo(Integer.class.getCanonicalName());
    }

    @Test
    void stopClosesSingletonResourceByConfiguredCloseMethod() throws Exception {
        CloseableResource closeableResource = new CloseableResource();
        NamingResourcesImpl namingResources = namingResourcesForServerWithGlobalResource("closeableResource",
                closeableResource);
        ContextResource resource = new ContextResource();
        resource.setName("closeableResource");
        resource.setCloseMethod("closeForNamingResources");

        namingResources.start();
        try {
            namingResources.addResource(resource);

            namingResources.stop();

            assertThat(closeableResource.isClosed()).isTrue();
        } finally {
            destroyQuietly(namingResources);
        }
    }

    private static NamingResourcesImpl namingResourcesForCurrentClassLoader() {
        StandardContext context = new StandardContext();
        context.setLoader(new CurrentClassLoader());

        NamingResourcesImpl namingResources = new NamingResourcesImpl();
        namingResources.setContainer(context);
        return namingResources;
    }

    private static ContextEnvironment environmentWithInjectionTarget(String resourceName, Class<?> targetClass,
            String targetName) {
        ContextEnvironment environment = new ContextEnvironment();
        environment.setName(resourceName);
        environment.setValue("configured");
        environment.addInjectionTarget(targetClass.getName(), targetName);
        return environment;
    }

    private static NamingResourcesImpl namingResourcesForServerWithGlobalResource(String name, Object value)
            throws NamingException {
        NamingContext globalNamingContext = new NamingContext(null, "global");
        globalNamingContext.bind(name, value);

        NamingResourcesImpl namingResources = new NamingResourcesImpl();
        StandardServer server = new StandardServer();
        server.setGlobalNamingContext(globalNamingContext);
        server.setGlobalNamingResources(namingResources);
        return namingResources;
    }

    private static void destroyQuietly(NamingResourcesImpl namingResources) throws LifecycleException {
        if (namingResources.getState().isAvailable()) {
            namingResources.stop();
        }
        namingResources.destroy();
    }

    public static class SetterInjectionTarget {

        public void setValue(String value) {
            // Test fixture for Tomcat's injection target introspection.
        }
    }

    public static class FieldInjectionTarget {

        public Integer value;
    }

    public static class CloseableResource {

        private boolean closed;

        public void closeForNamingResources() {
            closed = true;
        }

        public boolean isClosed() {
            return closed;
        }
    }

    private static class CurrentClassLoader implements Loader {

        private Context context;

        @Override
        public void backgroundProcess() {
            // No background processing is required for class loading in these tests.
        }

        @Override
        public ClassLoader getClassLoader() {
            return NamingResourcesImplTest.class.getClassLoader();
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
            // Delegation is not configurable for this fixed test class loader.
        }

        @Override
        public void addPropertyChangeListener(PropertyChangeListener listener) {
            // This lightweight loader has no mutable properties to publish.
        }

        @Override
        public boolean modified() {
            return false;
        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener listener) {
            // This lightweight loader has no mutable properties to publish.
        }
    }
}
