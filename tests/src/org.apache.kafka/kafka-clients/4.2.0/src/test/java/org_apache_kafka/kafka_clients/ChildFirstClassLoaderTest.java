/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.common.utils.ChildFirstClassLoader;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.Enumeration;

import static org.assertj.core.api.Assertions.assertThat;

public class ChildFirstClassLoaderTest {

    @Test
    void delegatesClassAndResourceLookupToParentWhenChildPathIsEmpty() throws Exception {
        ClassLoader parent = Thread.currentThread().getContextClassLoader();
        try (ChildFirstClassLoader loader = new ChildFirstClassLoader("", parent)) {
            Class<?> stringClass = loader.loadClass(String.class.getName());
            URL resource = loader.getResource("serialized-string.bin");
            Enumeration<URL> resources = loader.getResources("serialized-string.bin");

            assertThat(stringClass).isSameAs(String.class);
            assertThat(resource).isNotNull();
            assertThat(resources.hasMoreElements()).isTrue();
        }
    }
}
