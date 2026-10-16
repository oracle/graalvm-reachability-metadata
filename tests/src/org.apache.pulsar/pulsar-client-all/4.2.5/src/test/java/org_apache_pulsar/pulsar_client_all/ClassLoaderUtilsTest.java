/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.common.util.ClassLoaderUtils;
import org.junit.jupiter.api.Test;

public class ClassLoaderUtilsTest {
    @Test
    void loadsAndValidatesKnownApplicationClasses() throws Exception {
        final ClassLoader loader = getClass().getClassLoader();

        assertThat(ClassLoaderUtils.loadClass(Runnable.class.getName(), loader)).isEqualTo(Runnable.class);
        ClassLoaderUtils.implementsClass(Thread.class.getName(), Runnable.class, loader);
    }
}
