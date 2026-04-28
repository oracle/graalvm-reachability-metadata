/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mozilla.rhino;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.DefiningClassLoader;

import static org.assertj.core.api.Assertions.assertThat;

public class DefiningClassLoaderTest {
    @Test
    void loadClassUsesSystemClassLoaderWhenParentIsAbsent() throws ClassNotFoundException {
        DefiningClassLoader classLoader = new DefiningClassLoader(null);

        Class<?> loadedClass = classLoader.loadClass("java.lang.String", false);

        assertThat(loadedClass).isSameAs(String.class);
    }
}
