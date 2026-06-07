/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_hk2.hk2_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.glassfish.hk2.utilities.ClasspathDescriptorFileFinder;
import org.junit.jupiter.api.Test;

public class ClasspathDescriptorFileFinderTest {
    private static final String DESCRIPTOR_NAME = "coverage-test-descriptor";
    private static final String DESCRIPTOR_RESOURCE = "META-INF/hk2-locator/" + DESCRIPTOR_NAME;

    @Test
    void findDescriptorFilesLoadsNamedDescriptorsFromClasspath() throws IOException {
        final ClassLoader classLoader = ClasspathDescriptorFileFinderTest.class.getClassLoader();
        final ClasspathDescriptorFileFinder finder = new ClasspathDescriptorFileFinder(classLoader, DESCRIPTOR_NAME);

        final List<InputStream> descriptorFiles = finder.findDescriptorFiles();

        try {
            assertThat(descriptorFiles).hasSize(1);
            final InputStream descriptorFile = descriptorFiles.get(0);
            final String descriptorText = new String(descriptorFile.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(descriptorText).contains("org.example.CoverageDescriptor");
            assertThat(finder.getDescriptorFileInformation())
                    .hasSize(1)
                    .allSatisfy(identifier -> assertThat(identifier).contains(DESCRIPTOR_RESOURCE));
        } finally {
            for (InputStream descriptorFile : descriptorFiles) {
                descriptorFile.close();
            }
        }
    }
}
