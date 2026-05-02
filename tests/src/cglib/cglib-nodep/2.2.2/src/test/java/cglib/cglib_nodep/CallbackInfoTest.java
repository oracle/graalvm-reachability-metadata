/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib_nodep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;

import net.sf.cglib.proxy.Dispatcher;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.FixedValue;
import net.sf.cglib.proxy.InvocationHandler;
import net.sf.cglib.proxy.LazyLoader;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.NoOp;
import net.sf.cglib.proxy.ProxyRefDispatcher;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class CallbackInfoTest {
    @Test
    void resolvesLegacyClassLiteralHelperUsedByCallbackInfoInitialization() throws Exception {
        String callbackInfoClassName = Enhancer.class.getPackage().getName() + ".CallbackInfo";
        Class<?> callbackInfo = Enhancer.class.getClassLoader().loadClass(callbackInfoClassName);
        Method classLiteralHelper = callbackInfo.getDeclaredMethod("class$", String.class);
        classLiteralHelper.setAccessible(true);

        Object resolvedClass = classLiteralHelper.invoke(null, NoOp.class.getName());

        assertThat(resolvedClass).isSameAs(NoOp.class);
    }

    @Test
    void initializesCallbackInfoInIsolatedCglibLoader() throws Exception {
        try (ChildFirstCglibClassLoader loader = new ChildFirstCglibClassLoader(libraryLocation())) {
            Class<?> isolatedEnhancer = loader.loadClass(Enhancer.class.getName());
            Object enhancer = isolatedEnhancer.getConstructor().newInstance();
            Method setCallbackTypes = isolatedEnhancer.getMethod("setCallbackTypes", Class[].class);
            Class<?>[] callbackTypes = new Class<?>[] {
                    loader.loadClass(NoOp.class.getName()),
                    loader.loadClass(MethodInterceptor.class.getName()),
                    loader.loadClass(InvocationHandler.class.getName()),
                    loader.loadClass(LazyLoader.class.getName()),
                    loader.loadClass(Dispatcher.class.getName()),
                    loader.loadClass(FixedValue.class.getName()),
                    loader.loadClass(ProxyRefDispatcher.class.getName())
            };

            setCallbackTypes.invoke(enhancer, (Object) callbackTypes);
        } catch (InvocationTargetException exception) {
            if (!isUnsupportedNativeImageDynamicClassLoading(exception.getTargetException())) {
                throw exception;
            }
        } catch (Error error) {
            if (!isUnsupportedNativeImageDynamicClassLoading(error)) {
                throw error;
            }
        } catch (RuntimeException exception) {
            if (!isUnsupportedNativeImageDynamicClassLoading(exception)) {
                throw exception;
            }
        }
    }

    @Test
    void acceptsBuiltInCallbackTypesThroughEnhancerConfiguration() {
        Enhancer enhancer = new Enhancer();

        assertThatCode(() -> enhancer.setCallbackTypes(new Class[] {
                NoOp.class,
                MethodInterceptor.class,
                InvocationHandler.class,
                LazyLoader.class,
                Dispatcher.class,
                FixedValue.class,
                ProxyRefDispatcher.class
        })).doesNotThrowAnyException();
    }

    @Test
    void rejectsClassesThatAreNotEnhancerCallbacks() {
        Enhancer enhancer = new Enhancer();

        assertThatIllegalStateException()
                .isThrownBy(() -> enhancer.setCallbackType(NotACallback.class))
                .withMessageContaining("Unknown callback type")
                .withMessageContaining(NotACallback.class.getName());
    }

    private static URL libraryLocation() {
        CodeSource codeSource = Enhancer.class.getProtectionDomain().getCodeSource();
        assertThat(codeSource).isNotNull();
        return codeSource.getLocation();
    }

    private static boolean isUnsupportedNativeImageDynamicClassLoading(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof Error && NativeImageSupport.isUnsupportedFeatureError((Error) current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static class ChildFirstCglibClassLoader extends URLClassLoader {
        ChildFirstCglibClassLoader(URL libraryLocation) {
            super(new URL[] { libraryLocation }, CallbackInfoTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("net.sf.cglib.")) {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loadedClass = findLoadedClass(name);
                    if (loadedClass == null) {
                        try {
                            loadedClass = findClass(name);
                        } catch (ClassNotFoundException exception) {
                            loadedClass = super.loadClass(name, false);
                        }
                    }
                    if (resolve) {
                        resolveClass(loadedClass);
                    }
                    return loadedClass;
                }
            }
            return super.loadClass(name, resolve);
        }
    }

    public static class NotACallback {
    }
}
