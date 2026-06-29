/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.surefire_booter;

import org.apache.maven.surefire.booter.IsolatedClassLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class IsolatedClassLoaderTest {
    @Test
    void loadsExistingPlatformClassThroughChildDelegationFallback() throws Exception {
        try (IsolatedClassLoader classLoader = new IsolatedClassLoader(
                IsolatedClassLoaderTest.class.getClassLoader(), true, "child-first")) {
            Class<?> loadedClass = classLoader.loadClass("java.lang.String");
            Class<?> alreadyLoadedClass = classLoader.loadClass("java.lang.String");

            assertThat(loadedClass).isSameAs(String.class);
            assertThat(alreadyLoadedClass).isSameAs(loadedClass);
        }
    }

    @Test
    void delegatesToUrlClassLoaderWhenChildDelegationIsDisabled() throws Exception {
        try (IsolatedClassLoader classLoader = new IsolatedClassLoader(
                IsolatedClassLoaderTest.class.getClassLoader(), false, "parent-first")) {
            Class<?> loadedClass = classLoader.loadClass("java.lang.Integer");

            assertThat(loadedClass).isSameAs(Integer.class);
        }
    }
}
