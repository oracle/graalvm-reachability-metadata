/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.common.utils.ChildFirstClassLoader;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaCommonUtilsChildFirstClassLoaderTest {

    @TempDir
    Path classPathRoot;

    @Test
    void loadsParentClassAndEnumeratesChildFirstResources() throws Exception {
        Files.writeString(classPathRoot.resolve("child-first-resource.txt"), "child", StandardCharsets.UTF_8);

        try (ChildFirstClassLoader classLoader = new ChildFirstClassLoader(
                classPathRoot.toString(),
                OrgApacheKafkaCommonUtilsChildFirstClassLoaderTest.class.getClassLoader())) {
            Class<?> loadedClass = classLoader.loadClass(String.class.getName());
            URL resource = classLoader.getResource("child-first-resource.txt");
            List<URL> resources = Collections.list(classLoader.getResources("child-first-resource.txt"));

            assertThat(loadedClass).isSameAs(String.class);
            assertThat(resource).isNotNull();
            assertThat(resources).contains(resource);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }
}
