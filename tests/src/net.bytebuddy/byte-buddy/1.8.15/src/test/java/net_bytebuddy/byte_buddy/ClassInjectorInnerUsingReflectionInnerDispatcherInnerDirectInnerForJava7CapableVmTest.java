/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.dynamic.loading.ClassInjector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassInjectorInnerUsingReflectionInnerDispatcherInnerDirectInnerForJava7CapableVmTest {
    private static final String TYPE_NAME = "net_bytebuddy.byte_buddy.generated.Java7ReflectionLockedType";

    @Test
    void invokesConfiguredClassLoadingLockMethod() throws Exception {
        LockAwareClassLoader classLoader = new LockAwareClassLoader();
        DispatcherAccess access = DispatcherAccess.create();

        Object directLock = classLoader.exposedClassLoadingLock(TYPE_NAME);
        classLoader.clearLastRequestedName();
        Object dispatcherLock = access.getClassLoadingLock(classLoader, TYPE_NAME);

        assertThat(dispatcherLock).isSameAs(directLock);
        assertThat(classLoader.getLastRequestedName()).isEqualTo(TYPE_NAME);
    }

    private static class DispatcherAccess extends ClassInjector.UsingReflection {
        private final Dispatcher dispatcher;

        DispatcherAccess(Dispatcher dispatcher) {
            super(ClassLoader.getSystemClassLoader());
            this.dispatcher = dispatcher;
        }

        static DispatcherAccess create() throws Exception {
            Method getClassLoadingLock = LockAwareClassLoader.class.getMethod("exposedClassLoadingLock", String.class);
            return new DispatcherAccess(new DirectDispatcherBridge.Java7DirectDispatcher(getClassLoadingLock));
        }

        Object getClassLoadingLock(ClassLoader classLoader, String name) {
            return dispatcher.getClassLoadingLock(classLoader, name);
        }

        private static class DirectDispatcherBridge extends Dispatcher.Direct {
            DirectDispatcherBridge() throws Exception {
                super(unusedMethod(), unusedMethod(), unusedMethod(), unusedMethod());
            }

            @Override
            public Object getClassLoadingLock(ClassLoader classLoader, String name) {
                return classLoader;
            }

            @Override
            protected void onInitialization() {
                /* This bridge only exposes the inherited Java 7 dispatcher type. */
            }

            private static Method unusedMethod() throws NoSuchMethodException {
                return LockAwareClassLoader.class.getMethod("unusedReflectiveMethod", String.class);
            }

            private static class Java7DirectDispatcher extends ForJava7CapableVm {
                Java7DirectDispatcher(Method getClassLoadingLock) throws Exception {
                    super(unusedMethod(), unusedMethod(), unusedMethod(), unusedMethod(), getClassLoadingLock);
                }
            }
        }
    }

    public static class LockAwareClassLoader extends ClassLoader {
        private String lastRequestedName;

        public Object unusedReflectiveMethod(String name) {
            return name;
        }

        public Object exposedClassLoadingLock(String name) {
            lastRequestedName = name;
            return getClassLoadingLock(name);
        }

        void clearLastRequestedName() {
            lastRequestedName = null;
        }

        String getLastRequestedName() {
            return lastRequestedName;
        }
    }
}
