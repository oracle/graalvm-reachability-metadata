/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.net.URL;

import org.apache.pulsar.shade.javassist.ClassClassPath;
import org.junit.jupiter.api.Test;

public class ClassClassPathTest {
    @Test
    void findsAndOpensClassFileFromClassContext() throws Exception {
        ClassClassPath classPath = new ClassClassPath(ClassClassPath.class);
        URL resource = classPath.find("org.apache.pulsar.shade.javassist.ClassClassPath");

        assertThat(resource).isNotNull();
        try (InputStream classFile = classPath.openClassfile("org.apache.pulsar.shade.javassist.ClassClassPath")) {
            assertThat(classFile).isNotNull();
            assertThat(classFile.read()).isEqualTo(0xCA);
        }
    }
}
