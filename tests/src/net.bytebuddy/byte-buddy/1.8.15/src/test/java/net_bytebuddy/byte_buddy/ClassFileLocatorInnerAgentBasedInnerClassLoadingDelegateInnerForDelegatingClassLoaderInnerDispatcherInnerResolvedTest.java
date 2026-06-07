/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.dynamic.ClassFileLocator;
import org.junit.jupiter.api.Test;

import java.util.Vector;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassFileLocatorInnerAgentBasedInnerClassLoadingDelegateInnerForDelegatingClassLoaderInnerDispatcherInnerResolvedTest {
    @Test
    void resolvedDispatcherExtractsLoadedClassesFromConfiguredField() throws Exception {
        RecordingClassLoader classLoader = new RecordingClassLoader();
        classLoader.record(SampleLoadedType.class);

        Vector<Class<?>> extractedClasses = new DispatcherAccess(classLoader).extractLoadedClasses();

        assertThat(extractedClasses).isSameAs(classLoader.classes);
        assertThat(extractedClasses).containsExactly(SampleLoadedType.class);
    }

    private static class DispatcherAccess
            extends ClassFileLocator.AgentBased.ClassLoadingDelegate.ForDelegatingClassLoader {
        DispatcherAccess(ClassLoader classLoader) {
            super(classLoader);
        }

        Vector<Class<?>> extractLoadedClasses() throws NoSuchFieldException {
            Dispatcher dispatcher = new Dispatcher.Resolved(
                    RecordingClassLoader.class.getDeclaredField("classes")).initialize();
            return dispatcher.extract(getClassLoader());
        }
    }

    private static class RecordingClassLoader extends ClassLoader {
        private final Vector<Class<?>> classes = new java.util.Vector<Class<?>>();

        RecordingClassLoader() {
            super(RecordingClassLoader.class.getClassLoader());
        }

        void record(Class<?> type) {
            classes.add(type);
        }
    }

    private static class SampleLoadedType {
    }
}
