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
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassInjectorInnerUsingUnsafeInnerDispatcherInnerEnabledTest {
    private static final String GENERATED_PACKAGE = "net_bytebuddy.byte_buddy.generated.usingunsafe";

    @Test
    void enabledDispatcherDefinesClassThroughConfiguredUnsafe() throws Exception {
        try {
            UnsafeDefineClassAccess unsafe = UnsafeDefineClassAccess.locate();
            openUnsafePackageToTestModule(unsafe.unsafeType);

            InjectionClassLoader classLoader = new InjectionClassLoader();
            String typeName = GENERATED_PACKAGE + ".UnsafeInjectedType"
                    + Long.toUnsignedString(System.nanoTime());
            DynamicType.Unloaded<?> unloaded = new ByteBuddy(ClassFileVersion.JAVA_V8)
                    .subclass(Object.class)
                    .name(typeName)
                    .make();

            Class<?> loadedType = new UnsafeDispatcherAccess().defineClass(
                    classLoader,
                    typeName,
                    unloaded.getBytes(),
                    unsafe.theUnsafe,
                    unsafe.defineClass);

            assertThat(loadedType.getName()).isEqualTo(typeName);
            assertThat(loadedType.getClassLoader()).isSameAs(classLoader);
        } catch (ClassNotFoundException exception) {
            assertThat(exception.getMessage()).matches(".*(Unsafe|net\\.bytebuddy\\.agent\\.Installer).*");
        } catch (IllegalStateException exception) {
            if (!isUnsupportedDynamicClassDefinition(exception)) {
                assertThat(exception.getMessage()).contains("Byte Buddy agent");
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static boolean isUnsupportedDynamicClassDefinition(IllegalStateException exception) {
        Throwable cause = exception.getCause();
        return cause instanceof Error && NativeImageSupport.isUnsupportedFeatureError((Error) cause);
    }

    private static void openUnsafePackageToTestModule(Class<?> unsafeType) throws Exception {
        Module unsafeModule = unsafeType.getModule();
        if (!unsafeModule.isNamed()) {
            return;
        }
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
        Set<Module> testModules = Collections.singleton(
                ClassInjectorInnerUsingUnsafeInnerDispatcherInnerEnabledTest.class.getModule());
        instrumentation.redefineModule(
                unsafeModule,
                Collections.emptySet(),
                Collections.emptyMap(),
                Collections.singletonMap(unsafeType.getPackage().getName(), testModules),
                Collections.emptySet(),
                Collections.emptyMap());
    }

    private static class UnsafeDefineClassAccess {
        private final Class<?> unsafeType;
        private final Field theUnsafe;
        private final Method defineClass;

        UnsafeDefineClassAccess(Class<?> unsafeType, Field theUnsafe, Method defineClass) {
            this.unsafeType = unsafeType;
            this.theUnsafe = theUnsafe;
            this.defineClass = defineClass;
        }

        static UnsafeDefineClassAccess locate()
                throws ClassNotFoundException, NoSuchFieldException, NoSuchMethodException {
            try {
                return of(Class.forName("sun.misc.Unsafe"));
            } catch (ClassNotFoundException exception) {
                return of(Class.forName("jdk.internal.misc.Unsafe"));
            } catch (NoSuchMethodException exception) {
                return of(Class.forName("jdk.internal.misc.Unsafe"));
            }
        }

        private static UnsafeDefineClassAccess of(Class<?> unsafeType)
                throws NoSuchFieldException, NoSuchMethodException {
            return new UnsafeDefineClassAccess(
                    unsafeType,
                    unsafeType.getDeclaredField("theUnsafe"),
                    unsafeType.getMethod(
                            "defineClass",
                            String.class,
                            byte[].class,
                            int.class,
                            int.class,
                            ClassLoader.class,
                            ProtectionDomain.class));
        }
    }

    private static class UnsafeDispatcherAccess extends ClassInjector.UsingUnsafe {
        UnsafeDispatcherAccess() {
            super(ClassLoader.getSystemClassLoader());
        }

        Class<?> defineClass(
                ClassLoader classLoader,
                String name,
                byte[] binaryRepresentation,
                Field theUnsafe,
                Method defineClass) {
            Dispatcher dispatcher = new EnabledDispatcher(theUnsafe, defineClass).initialize();
            return dispatcher.defineClass(
                    classLoader,
                    name,
                    binaryRepresentation,
                    UnsafeDispatcherAccess.class.getProtectionDomain());
        }

        private static class EnabledDispatcher extends Dispatcher.Enabled {
            EnabledDispatcher(Field theUnsafe, Method defineClass) {
                super(theUnsafe, defineClass);
            }
        }
    }

    private static class InjectionClassLoader extends ClassLoader {
        InjectionClassLoader() {
            super(Thread.currentThread().getContextClassLoader());
        }
    }
}
