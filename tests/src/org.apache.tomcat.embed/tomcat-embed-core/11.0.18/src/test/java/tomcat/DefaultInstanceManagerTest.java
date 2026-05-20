/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.beans.PropertyChangeListener;
import java.util.Hashtable;
import java.util.Map;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.apache.catalina.Context;
import org.apache.catalina.Loader;
import org.apache.catalina.core.DefaultInstanceManager;
import org.apache.catalina.core.StandardContext;
import org.apache.naming.NamingContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultInstanceManagerTest {

    @Test
    void constructsComponentsAndInvokesInjectionLifecycleCallbacks() throws Exception {
        NamingContext namingContext = namingContext(Map.of(
                "managedField", "field resource",
                "managedSetter", "setter resource"));
        DefaultInstanceManager manager = instanceManager(namingContext, testClassLoader(), testClassLoader());

        ManagedLifecycleComponent component = (ManagedLifecycleComponent) manager.newInstance(
                ManagedLifecycleComponent.class);

        assertThat(component.getManagedField()).isEqualTo("field resource");
        assertThat(component.getManagedSetter()).isEqualTo("setter resource");
        assertThat(component.isPostConstructCalled()).isTrue();
        assertThat(component.isPreDestroyCalled()).isFalse();

        manager.destroyInstance(component);

        assertThat(component.isPreDestroyCalled()).isTrue();
    }

    @Test
    void loadsApplicationClassesWithWebappClassLoaderWhenContainerClassLoaderCannotSeeThem() throws Exception {
        DefaultInstanceManager manager = instanceManager(new NamingContext(new Hashtable<>(), "empty"),
                testClassLoader(), new RejectingClassLoader());

        Object component = manager.newInstance(WebappLoadedComponent.class.getName());

        assertThat(component).isInstanceOf(WebappLoadedComponent.class);
    }

    @Test
    void loadsCatalinaClassesWithContainerClassLoader() throws Exception {
        DefaultInstanceManager manager = instanceManager(new NamingContext(new Hashtable<>(), "empty"),
                testClassLoader(), testClassLoader());

        Object component = manager.newInstance(StandardContext.class.getName());

        assertThat(component).isInstanceOf(StandardContext.class);
    }

    @Test
    void constructsClassesWithExplicitClassLoader() throws Exception {
        DefaultInstanceManager manager = instanceManager(new NamingContext(new Hashtable<>(), "empty"),
                testClassLoader(), testClassLoader());

        Object component = manager.newInstance(ExplicitLoaderComponent.class.getName(), testClassLoader());

        assertThat(component).isInstanceOf(ExplicitLoaderComponent.class);
    }

    private static NamingContext namingContext(Map<String,Object> bindings) throws Exception {
        NamingContext context = new NamingContext(new Hashtable<>(), "default-instance-manager-test");
        for (Map.Entry<String,Object> binding : bindings.entrySet()) {
            context.bind(binding.getKey(), binding.getValue());
        }
        return context;
    }

    private static DefaultInstanceManager instanceManager(NamingContext namingContext, ClassLoader webappParent,
            ClassLoader containerClassLoader) {
        StandardContext context = new StandardContext();
        context.setName("default-instance-manager-test");
        context.setPath("/default-instance-manager-test");
        context.setPrivileged(true);

        context.setLoader(new TestLoader(webappParent));

        return new DefaultInstanceManager(namingContext, Map.of(), context, containerClassLoader);
    }

    private static ClassLoader testClassLoader() {
        return DefaultInstanceManagerTest.class.getClassLoader();
    }

    public static final class ManagedLifecycleComponent {
        @Resource(name = "managedField")
        private String managedField;

        private String managedSetter;
        private boolean postConstructCalled;
        private boolean preDestroyCalled;

        public ManagedLifecycleComponent() {
        }

        public String getManagedField() {
            return managedField;
        }

        public String getManagedSetter() {
            return managedSetter;
        }

        public boolean isPostConstructCalled() {
            return postConstructCalled;
        }

        public boolean isPreDestroyCalled() {
            return preDestroyCalled;
        }

        @Resource(name = "managedSetter")
        public void setManagedSetter(String managedSetter) {
            this.managedSetter = managedSetter;
        }

        @PostConstruct
        private void initialize() {
            postConstructCalled = true;
        }

        @PreDestroy
        private void release() {
            preDestroyCalled = true;
        }
    }

    public static final class WebappLoadedComponent {
        public WebappLoadedComponent() {
        }
    }

    public static final class ExplicitLoaderComponent {
        public ExplicitLoaderComponent() {
        }
    }

    private static final class TestLoader implements Loader {
        private final ClassLoader classLoader;
        private Context context;
        private boolean delegate;

        private TestLoader(ClassLoader classLoader) {
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
            return delegate;
        }

        @Override
        public void setDelegate(boolean delegate) {
            this.delegate = delegate;
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

    private static final class RejectingClassLoader extends ClassLoader {
        private RejectingClassLoader() {
            super(null);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            throw new ClassNotFoundException(name);
        }
    }
}
