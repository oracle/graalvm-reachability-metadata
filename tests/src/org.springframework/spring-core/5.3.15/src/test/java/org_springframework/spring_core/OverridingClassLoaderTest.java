/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.OverridingClassLoader;

/** Verifies Spring's public overriding class loader behavior. §FS-repository-functional-spec.5.2 */
public class OverridingClassLoaderTest {
    private static final String ABSENT_CLASS_NAME = "org_springframework.spring_core.AbsentOverriddenType";

    @Test
    void loadsEligibleClassThroughOverrideDelegate() throws ClassNotFoundException {
        ClassLoader applicationClassLoader = OverridingClassLoader.class.getClassLoader();
        OverridingClassLoader classLoader =
                new OverridingClassLoader(applicationClassLoader, applicationClassLoader);

        Class<?> loadedClass = classLoader.loadClass(OverridingClassLoader.class.getName());

        assertThat(loadedClass).isSameAs(OverridingClassLoader.class);
    }

    @Test
    void loadsExcludedClassThroughParent() throws ClassNotFoundException {
        OverridingClassLoader classLoader =
                new OverridingClassLoader(OverridingClassLoader.class.getClassLoader());

        Class<?> loadedClass = classLoader.loadClass(String.class.getName());

        assertThat(loadedClass).isSameAs(String.class);
    }

    @Test
    void reportsEligibleClassThatIsNotAvailable() {
        OverridingClassLoader classLoader =
                new OverridingClassLoader(OverridingClassLoader.class.getClassLoader());
        ClassNotFoundException failure = null;

        try {
            classLoader.loadClass(ABSENT_CLASS_NAME);
        } catch (ClassNotFoundException exception) {
            failure = exception;
        }

        assertThat(failure).isNotNull();
    }
}
