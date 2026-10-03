/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_client;

import java.io.InputStream;

import org.apache.curator.shaded.com.google.common.reflect.ClassPath;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassPathInnerResourceInfoTest {
    private static final String RESOURCE_NAME = ClassPath.class.getName().replace('.', '/') + ".class";

    @Test
    void opensAResourceDiscoveredOnTheLibraryClasspath() throws Exception {
        ClassPath.ResourceInfo resource = ClassPath.from(ClassPath.class.getClassLoader()).getResources().stream()
                .filter(candidate -> RESOURCE_NAME.equals(candidate.getResourceName()))
                .findFirst()
                .orElseThrow();

        assertThat(resource.getResourceName()).isEqualTo(RESOURCE_NAME);
        assertThat(resource.url().toString()).endsWith(RESOURCE_NAME);
        try (InputStream input = resource.asByteSource().openStream()) {
            assertThat(input.readNBytes(4)).containsExactly(0xca, 0xfe, 0xba, 0xbe);
        }
    }
}
