/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Vector;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.bytebuddy.dynamic.ClassFileLocator.ForInstrumentation.ClassLoadingDelegate.ForDelegatingClassLoader.Dispatcher;
import org.modelmapper.internal.bytebuddy.dynamic.ClassFileLocator.ForInstrumentation.ClassLoadingDelegate.ForDelegatingClassLoader.Dispatcher.Resolved;

public class ClassFileLocatorInnerForInstrumentationInnerClassLoadingDelegateInnerForDelegatingClassLoaderInnerDispatcherInnerResolvedTest {
    @Test
    void extractsLoadedClassesFromConfiguredField() throws Exception {
        RecordingClassLoader classLoader = new RecordingClassLoader(getClass().getClassLoader());
        classLoader.classes.add(SampleType.class);
        Field classesField = RecordingClassLoader.class.getField("classes");
        Dispatcher dispatcher = new Resolved(classesField).initialize();

        Vector<Class<?>> extractedClasses = dispatcher.extract(classLoader);

        assertThat(extractedClasses).containsExactly(SampleType.class);
    }

    public static class RecordingClassLoader extends ClassLoader {
        public final Vector<Class<?>> classes = new Vector<>();

        RecordingClassLoader(ClassLoader parent) {
            super(parent);
        }
    }

    private static class SampleType {
    }
}
