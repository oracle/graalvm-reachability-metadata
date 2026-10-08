/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_sshd.sshd_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.sshd.common.util.io.resource.ClassLoaderResource;
import org.junit.jupiter.api.Test;

public class ClassLoaderResourceTest {
    @Test
    void opensAResourceFromTheLibraryClassLoader() throws IOException {
        ClassLoaderResource resource = new ClassLoaderResource(
                ClassLoaderResourceTest.class.getClassLoader(),
                "org/apache/sshd/sshd-version.properties");
        Properties properties = new Properties();
        try (InputStream input = resource.openInputStream()) {
            properties.load(input);
        }

        assertThat(resource.getName()).isEqualTo("org/apache/sshd/sshd-version.properties");
        assertThat(properties.stringPropertyNames())
                .contains("groupId", "artifactId", "version", "sshd-version");
    }
}
