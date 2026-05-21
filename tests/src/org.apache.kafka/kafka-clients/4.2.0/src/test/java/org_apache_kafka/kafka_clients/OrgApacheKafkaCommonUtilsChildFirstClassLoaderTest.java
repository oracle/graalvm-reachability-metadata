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
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaCommonUtilsChildFirstClassLoaderTest {

    @Test
    void delegatesClassesAndResourcesToParentWhenChildPathIsEmpty() throws Exception {
        ClassLoader parent = Thread.currentThread().getContextClassLoader();
        try (ChildFirstClassLoader classLoader = new ChildFirstClassLoader("", parent)) {
            Class<?> stringClass = classLoader.loadClass(String.class.getName());
            URL logbackResource = classLoader.getResource("logback.xml");
            Enumeration<URL> resourceEnumeration = classLoader.getResources("logback.xml");
            List<URL> resources = Collections.list(resourceEnumeration);

            assertThat(stringClass).isSameAs(String.class);
            assertThat(logbackResource).isNotNull();
            assertThat(resources).isNotEmpty();
        }
    }
}
