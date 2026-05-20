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
    void delegatesClassAndResourceLookupToParent() throws Exception {
        ClassLoader parent = Thread.currentThread().getContextClassLoader();
        try (ChildFirstClassLoader loader = new ChildFirstClassLoader("", parent)) {
            assertThat(loader.loadClass(String.class.getName())).isSameAs(String.class);
            assertThat(loader.getResource("logback.xml")).isNotNull();

            Enumeration<URL> resources = loader.getResources("logback.xml");
            assertThat(resources.hasMoreElements()).isTrue();
        }
    }
}
