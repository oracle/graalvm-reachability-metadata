/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_guice;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.matcher.Matchers;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.URL;
import java.net.URLClassLoader;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class BytecodeGenInnerBridgeClassLoaderTest {
    private static final String ISOLATED_SERVICE_CLASS_NAME =
            "org_sonatype_sisu.sisu_guice.BytecodeGenInnerBridgeClassLoaderTest$IsolatedAopService";

    @Test
    void interceptsPublicServiceLoadedFromChildClassLoader() throws Exception {
        try {
            URL classPathRoot = BytecodeGenInnerBridgeClassLoaderTest.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation();
            URL[] classPathRoots = {classPathRoot};
            try (ChildFirstServiceLoader loader = new ChildFirstServiceLoader(classPathRoots)) {
                Class<?> serviceClass = loader.loadClass(ISOLATED_SERVICE_CLASS_NAME);
                Injector injector = Guice.createInjector(new InterceptorModule(serviceClass));

                GreetingService service = (GreetingService) injector.getInstance(serviceClass);

                assertThat(service.greet("bridge")).isEqualTo("intercepted hello bridge");
                assertThat(service.getClass().getClassLoader()).isNotSameAs(loader);
            }
        } catch (Error error) {
            rethrowUnlessUnsupportedDynamicClassLoading(error);
        }
    }

    public interface GreetingService {
        String greet(String name);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Intercepted {
    }

    public static class IsolatedAopService implements GreetingService {
        public IsolatedAopService() {
        }

        @Override
        @Intercepted
        public String greet(String name) {
            return "hello " + name;
        }
    }

    private static final class InterceptorModule extends AbstractModule {
        private final Class<?> serviceClass;

        private InterceptorModule(Class<?> serviceClass) {
            this.serviceClass = serviceClass;
        }

        @Override
        @SuppressWarnings({"rawtypes", "unchecked"})
        protected void configure() {
            bind((Class) serviceClass);
            bindInterceptor(
                    Matchers.subclassesOf((Class) serviceClass),
                    Matchers.annotatedWith(Intercepted.class),
                    new PrefixingInterceptor());
        }
    }

    private static final class PrefixingInterceptor implements MethodInterceptor {
        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            return "intercepted " + invocation.proceed();
        }
    }

    private static final class ChildFirstServiceLoader extends URLClassLoader {
        private ChildFirstServiceLoader(URL[] urls) {
            super(urls, BytecodeGenInnerBridgeClassLoaderTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!ISOLATED_SERVICE_CLASS_NAME.equals(name)) {
                return super.loadClass(name, resolve);
            }

            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    loadedClass = findClass(name);
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }
    }

    private static void rethrowUnlessUnsupportedDynamicClassLoading(Error error) {
        if (NativeImageSupport.isUnsupportedFeatureError(error)) {
            return;
        }
        throw error;
    }
}
