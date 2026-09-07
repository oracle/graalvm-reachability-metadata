/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.type.TypeFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class TypeFactoryTest {
    @Test
    void resolvesAClassByNameWithDefaultAndExplicitClassLoaders() throws Exception {
        TypeFactory factory = TypeFactory.defaultInstance();

        assertThat(factory.withClassLoader(getClass().getClassLoader()).findClass("java.lang.Integer"))
            .isEqualTo(Integer.class);

        Thread thread = Thread.currentThread();
        ClassLoader contextLoader = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(null);
            assertThat(factory.findClass("java.lang.String")).isEqualTo(String.class);
        } finally {
            thread.setContextClassLoader(contextLoader);
        }
    }
}
