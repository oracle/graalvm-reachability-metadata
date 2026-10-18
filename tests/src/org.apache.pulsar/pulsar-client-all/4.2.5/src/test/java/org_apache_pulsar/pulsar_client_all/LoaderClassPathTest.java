/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import org.apache.pulsar.shade.javassist.ClassPool;
import org.apache.pulsar.shade.javassist.LoaderClassPath;
import org.junit.jupiter.api.Test;

public class LoaderClassPathTest {

    @Test
    void findsAndReadsAClassThroughTheApplicationLoader() throws Exception {
        final LoaderClassPath path = new LoaderClassPath(ClassPool.class.getClassLoader());

        assertThat(path.find(ClassPool.class.getName())).isNotNull();
        try (InputStream stream = path.openClassfile(ClassPool.class.getName())) {
            assertThat(stream).isNotNull();
            assertThat(stream.read()).isEqualTo(0xCA);
        }
    }
}
