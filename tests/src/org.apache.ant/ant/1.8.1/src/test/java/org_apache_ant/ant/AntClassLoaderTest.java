/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.apache.tools.ant.AntClassLoader;
import org.junit.jupiter.api.Test;

public class AntClassLoaderTest {
    private static final String TASK_DEFINITIONS = "org/apache/tools/ant/taskdefs/defaults.properties";

    @Test
    void initializesClassThroughPublicApi() {
        AntClassLoader.initializeClass(ConstructorProbe.class);
    }

    @Test
    void loadsSystemClassesThroughConfiguredParent() throws ClassNotFoundException {
        AntClassLoader loader = new AntClassLoader(getClass().getClassLoader(), true);

        assertThat(loader.forceLoadSystemClass(String.class.getName())).isSameAs(String.class);
        assertThat(loader.loadClass(String.class.getName())).isSameAs(String.class);
    }

    @Test
    void findsResourcesThroughParentLoader() throws Exception {
        AntClassLoader loader = new AntClassLoader(getClass().getClassLoader(), true);

        URL resource = loader.getResource(TASK_DEFINITIONS);
        assertThat(resource).isNotNull();
        try (InputStream stream = loader.getResourceAsStream(TASK_DEFINITIONS);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(stream, StandardCharsets.ISO_8859_1))) {
            assertThat(reader.readLine()).isNotEmpty();
        }

        loader.setParentFirst(false);
        assertThat(loader.getResource(TASK_DEFINITIONS)).isNotNull();
        assertThat(loader.getNamedResources(TASK_DEFINITIONS).hasMoreElements()).isTrue();
    }

    public static class ConstructorProbe {
        public ConstructorProbe() {
        }
    }
}
