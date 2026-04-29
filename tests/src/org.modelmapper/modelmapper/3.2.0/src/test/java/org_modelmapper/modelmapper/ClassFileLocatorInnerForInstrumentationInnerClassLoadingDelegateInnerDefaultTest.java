/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.bytebuddy.dynamic.ClassFileLocator.ForInstrumentation.ClassLoadingDelegate;
import org.modelmapper.internal.bytebuddy.dynamic.ClassFileLocator.ForInstrumentation.ClassLoadingDelegate.Default;

public class ClassFileLocatorInnerForInstrumentationInnerClassLoadingDelegateInnerDefaultTest {
    @Test
    void locatesTypeWithProvidedClassLoader() throws ClassNotFoundException {
        ClassLoader classLoader = ClassFileLocatorInnerForInstrumentationInnerClassLoadingDelegateInnerDefaultTest.class
                .getClassLoader();
        ClassLoadingDelegate delegate = Default.of(classLoader);

        Class<?> locatedType = delegate.locate(SampleType.class.getName());

        assertThat(locatedType).isSameAs(SampleType.class);
        assertThat(delegate.getClassLoader()).isSameAs(classLoader);
    }

    public static class SampleType {
    }
}
