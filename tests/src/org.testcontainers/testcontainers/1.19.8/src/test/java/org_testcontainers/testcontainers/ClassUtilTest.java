/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.util.ClassUtil;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassUtilTest {
    @Test
    void resolvesAndInstantiatesClasses() throws Exception {
        assertThat(ClassUtil.findConstructor(Bean.class, true)).isNotNull();
        assertThat(ClassUtil.createInstance(Bean.class, true)).isInstanceOf(Bean.class);
        assertThat(ClassUtil.findClass("java.lang.String")).isEqualTo(String.class);

        Thread thread = Thread.currentThread();
        ClassLoader contextLoader = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(null);
            assertThat(ClassUtil.findClass("java.lang.Integer")).isEqualTo(Integer.class);
        } finally {
            thread.setContextClassLoader(contextLoader);
        }
    }

    public static class Bean {
        public Bean() {}
    }
}
