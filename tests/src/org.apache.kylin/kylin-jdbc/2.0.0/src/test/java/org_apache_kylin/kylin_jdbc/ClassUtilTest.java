/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.util.ClassUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassUtilTest {
    @Test
    void locatesAndCreatesAConcreteType() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader originalLoader = thread.getContextClassLoader();
        ClassLoader testLoader = ClassUtilTest.class.getClassLoader();
        assertThat(testLoader).isNotNull();

        try {
            thread.setContextClassLoader(testLoader);
            assertThat(ClassUtil.findClass(Constructible.class.getName())).isEqualTo(Constructible.class);

            thread.setContextClassLoader(null);
            assertThat(ClassUtil.findClass(Constructible.class.getName())).isEqualTo(Constructible.class);
        } finally {
            thread.setContextClassLoader(originalLoader);
        }
        assertThat(ClassUtil.createInstance(Constructible.class, true).value).isEqualTo("created");
    }

    public static class Constructible {
        public final String value = "created";

        public Constructible() {
        }
    }
}
