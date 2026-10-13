/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tngtech_archunit.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.ClassLoaders;
import org.junit.jupiter.api.Test;

public class ClassLoadersTest {
    @Test
    void loadsAnApplicationClassByNameFromTheCurrentContext() throws Exception {
        Class<?> loaded = ClassLoaders.loadClass(AnnotatedFixture.class.getName());

        assertThat(loaded).isSameAs(AnnotatedFixture.class);
        assertThat(ClassLoaders.getCurrentClassLoader(loaded))
                .isSameAs(Thread.currentThread().getContextClassLoader());
    }

    @Test
    void fallsBackToTheDefiningLoaderWhenTheContextCannotLoadTheClass() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader originalContextLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(ClassLoader.getPlatformClassLoader());
        try {
            Class<?> loaded = ClassLoaders.loadClass(AnnotatedFixture.class.getName());

            assertThat(loaded).isSameAs(AnnotatedFixture.class);
        } finally {
            thread.setContextClassLoader(originalContextLoader);
        }
    }
}
