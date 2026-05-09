/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.surefire_booter;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.maven.surefire.booter.IsolatedClassLoader;
import org.junit.jupiter.api.Test;

public class IsolatedClassLoaderTest {

    @Test
    public void loadClassDelegatesToSystemClassLoaderWhenChildLookupMisses() throws Exception {
        try (IsolatedClassLoader classLoader = new IsolatedClassLoader(
                Thread.currentThread().getContextClassLoader(), true, "child-first")) {
            Class<?> loadedClass = classLoader.loadClass(String.class.getName());

            assertThat(loadedClass).isSameAs(String.class);
            assertThat(classLoader.toString()).isEqualTo("IsolatedClassLoader{roleName='child-first'}");
        }
    }

    @Test
    public void loadClassUsesParentFirstUrlClassLoaderBehaviorWhenChildDelegationIsDisabled() throws Exception {
        ClassLoader parent = Thread.currentThread().getContextClassLoader();
        try (IsolatedClassLoader classLoader = new IsolatedClassLoader(parent, false, "parent-first")) {
            Class<?> loadedClass = classLoader.loadClass(String.class.getName());

            assertThat(loadedClass).isSameAs(String.class);
        }
    }
}
