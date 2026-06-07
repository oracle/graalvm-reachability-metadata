/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.dynamic.loading.ClassInjector;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassInjectorInnerUsingReflectionInnerDispatcherInnerUnavailableTest {
    @Test
    void unavailableDispatcherFindsClassesThroughClassLoaderLoadClass() {
        TrackingClassLoader classLoader = new TrackingClassLoader();
        UnavailableDispatcherAccess access = UnavailableDispatcherAccess.create();

        Class<?> loadedType = access.findClass(classLoader, String.class.getName());
        Class<?> missingType = access.findClass(classLoader, "net.bytebuddy.generated.DoesNotExist");

        assertThat(loadedType).isSameAs(String.class);
        assertThat(missingType).isNull();
        assertThat(classLoader.requestedNames()).containsExactly(
                String.class.getName(),
                "net.bytebuddy.generated.DoesNotExist");
    }

    private static class UnavailableDispatcherAccess extends ClassInjector.UsingReflection {
        private final Dispatcher dispatcher;

        UnavailableDispatcherAccess(Dispatcher dispatcher) {
            super(ClassLoader.getSystemClassLoader());
            this.dispatcher = dispatcher;
        }

        static UnavailableDispatcherAccess create() {
            return new UnavailableDispatcherAccess(
                    new ExposedUnavailableDispatcher(new IllegalStateException("reflection unavailable")));
        }

        Class<?> findClass(ClassLoader classLoader, String name) {
            return dispatcher.findClass(classLoader, name);
        }

        private static class ExposedUnavailableDispatcher extends Dispatcher.Unavailable {
            ExposedUnavailableDispatcher(Exception exception) {
                super(exception);
            }
        }
    }

    private static class TrackingClassLoader extends ClassLoader {
        private final List<String> requestedNames = new ArrayList<String>();

        TrackingClassLoader() {
            super(Thread.currentThread().getContextClassLoader());
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            requestedNames.add(name);
            return super.loadClass(name);
        }

        List<String> requestedNames() {
            return requestedNames;
        }
    }
}
