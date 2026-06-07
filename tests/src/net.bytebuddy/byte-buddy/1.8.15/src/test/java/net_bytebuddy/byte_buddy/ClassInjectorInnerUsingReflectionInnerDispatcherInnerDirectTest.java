/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassInjector;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassInjectorInnerUsingReflectionInnerDispatcherInnerDirectTest {
    @Test
    void directDispatcherDefinesPackageAndClassThroughClassLoaderMethods() throws Exception {
        try {
            openJavaLangPackageToTestModule();
            DirectDispatcherAccess access = DirectDispatcherAccess.create();
            InjectionClassLoader classLoader = new InjectionClassLoader();
            String packageName = "net.bytebuddy.generated.direct" + Long.toUnsignedString(System.nanoTime());
            String typeName = packageName + ".ReflectionInjectedType";
            DynamicType.Unloaded<?> unloaded = new ByteBuddy(ClassFileVersion.JAVA_V8)
                    .subclass(Object.class)
                    .name(typeName)
                    .make();

            assertThat(access.findClass(classLoader, typeName)).isNull();
            assertThat(access.getPackage(classLoader, packageName)).isNull();

            Package definedPackage = access.definePackage(classLoader, packageName);
            assertThat(definedPackage.getName()).isEqualTo(packageName);
            assertThat(access.getPackage(classLoader, packageName)).isSameAs(definedPackage);

            Class<?> loadedType = access.defineClass(classLoader, typeName, unloaded.getBytes());
            assertThat(loadedType.getName()).isEqualTo(typeName);
            assertThat(loadedType.getClassLoader()).isSameAs(classLoader);
            assertThat(loadedType.getPackage()).isSameAs(definedPackage);
            assertThat(access.findClass(classLoader, typeName)).isSameAs(loadedType);
        } catch (ClassNotFoundException exception) {
            assertThat(exception).hasMessageContaining("net.bytebuddy.agent.Installer");
        } catch (IllegalStateException exception) {
            assertThat(exception.getMessage()).matches(".*(direct reflection dispatcher|Byte Buddy agent).*");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static void openJavaLangPackageToTestModule() throws Exception {
        Class<?> installer = ClassLoader.getSystemClassLoader().loadClass("net.bytebuddy.agent.Installer");
        Method getInstrumentation = installer.getMethod("getInstrumentation");
        Instrumentation instrumentation;
        try {
            instrumentation = (Instrumentation) getInstrumentation.invoke(null);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof IllegalStateException) {
                throw (IllegalStateException) exception.getCause();
            }
            throw exception;
        }
        Module javaBaseModule = ClassLoader.class.getModule();
        Set<Module> testModules = Collections.singleton(DirectDispatcherAccess.class.getModule());
        instrumentation.redefineModule(
                javaBaseModule,
                Collections.emptySet(),
                Collections.emptyMap(),
                Collections.singletonMap("java.lang", testModules),
                Collections.emptySet(),
                Collections.emptyMap());
    }

    private static class DirectDispatcherAccess extends ClassInjector.UsingReflection {
        private final Dispatcher dispatcher;

        DirectDispatcherAccess(Dispatcher dispatcher) {
            super(ClassLoader.getSystemClassLoader());
            this.dispatcher = dispatcher;
        }

        static DirectDispatcherAccess create() throws Exception {
            Dispatcher dispatcher = new DirectDispatcher().initialize();
            if (dispatcher instanceof Dispatcher.Initializable
                    && !((Dispatcher.Initializable) dispatcher).isAvailable()) {
                throw new IllegalStateException("The direct reflection dispatcher is not available");
            }
            return new DirectDispatcherAccess(dispatcher);
        }

        Class<?> findClass(ClassLoader classLoader, String name) {
            return dispatcher.findClass(classLoader, name);
        }

        Class<?> defineClass(ClassLoader classLoader, String name, byte[] binaryRepresentation) {
            ProtectionDomain protectionDomain = DirectDispatcherAccess.class.getProtectionDomain();
            return dispatcher.defineClass(classLoader, name, binaryRepresentation, protectionDomain);
        }

        Package getPackage(ClassLoader classLoader, String name) {
            return dispatcher.getPackage(classLoader, name);
        }

        Package definePackage(ClassLoader classLoader, String name) {
            return dispatcher.definePackage(
                    classLoader,
                    name,
                    "Byte Buddy Direct Dispatcher Specification",
                    "1",
                    "Byte Buddy",
                    "Byte Buddy Direct Dispatcher Implementation",
                    "1",
                    "Byte Buddy",
                    (URL) null);
        }

        private static class DirectDispatcher extends Dispatcher.Direct {
            DirectDispatcher() throws Exception {
                super(findLoadedClassMethod(), defineClassMethod(), getPackageMethod(), definePackageMethod());
            }

            @Override
            public Object getClassLoadingLock(ClassLoader classLoader, String name) {
                return classLoader;
            }

            @Override
            protected void onInitialization() {
                /* No additional methods are needed for the direct call sites exercised here. */
            }

            private static Method findLoadedClassMethod() throws NoSuchMethodException {
                return ClassLoader.class.getDeclaredMethod("findLoadedClass", String.class);
            }

            private static Method defineClassMethod() throws NoSuchMethodException {
                return ClassLoader.class.getDeclaredMethod(
                        "defineClass",
                        String.class,
                        byte[].class,
                        int.class,
                        int.class,
                        ProtectionDomain.class);
            }

            private static Method getPackageMethod() throws NoSuchMethodException {
                try {
                    return ClassLoader.class.getMethod("getDefinedPackage", String.class);
                } catch (NoSuchMethodException ignored) {
                    return ClassLoader.class.getDeclaredMethod("getPackage", String.class);
                }
            }

            private static Method definePackageMethod() throws NoSuchMethodException {
                return ClassLoader.class.getDeclaredMethod(
                        "definePackage",
                        String.class,
                        String.class,
                        String.class,
                        String.class,
                        String.class,
                        String.class,
                        String.class,
                        URL.class);
            }
        }
    }

    private static class InjectionClassLoader extends ClassLoader {
        InjectionClassLoader() {
            super(Thread.currentThread().getContextClassLoader());
        }
    }
}
