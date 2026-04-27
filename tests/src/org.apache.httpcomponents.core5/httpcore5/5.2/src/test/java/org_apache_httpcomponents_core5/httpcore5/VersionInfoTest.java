/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_httpcomponents_core5.httpcore5;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.util.VersionInfo;
import org.junit.jupiter.api.Test;

public class VersionInfoTest {

    private static final String PACKAGE_NAME = "metadata.forge.versioninfo";
    private static final String RESOURCE_NAME = "metadata/forge/versioninfo/version.properties";
    private static final String MODULE_NAME = "httpcore-test-module";
    private static final String RELEASE_NAME = "coverage-release";

    @Test
    void loadVersionInfoReadsVersionPropertiesFromProvidedClassLoader() {
        final String versionProperties = """
                info.module=httpcore-test-module
                info.release=coverage-release
                """;
        final ClassLoader classLoader = new VersionPropertiesClassLoader(RESOURCE_NAME, versionProperties);

        final VersionInfo versionInfo = VersionInfo.loadVersionInfo(PACKAGE_NAME, classLoader);

        assertThat(versionInfo).isNotNull();
        assertThat(versionInfo.getPackage()).isEqualTo(PACKAGE_NAME);
        assertThat(versionInfo.getModule()).isEqualTo(MODULE_NAME);
        assertThat(versionInfo.getRelease()).isEqualTo(RELEASE_NAME);
        assertThat(versionInfo.getTimestamp()).isEqualTo(VersionInfo.UNAVAILABLE);
        assertThat(versionInfo.getClassloader()).isEqualTo(classLoader.toString());
        assertThat(versionInfo).hasToString(
                "VersionInfo(" + PACKAGE_NAME + ':' + MODULE_NAME + ':' + RELEASE_NAME + ")@" + classLoader);
    }

    private static final class VersionPropertiesClassLoader extends ClassLoader {
        private final String resourceName;
        private final byte[] resourceBytes;

        private VersionPropertiesClassLoader(final String resourceName, final String resourceContent) {
            super(null);
            this.resourceName = resourceName;
            this.resourceBytes = resourceContent.getBytes(StandardCharsets.ISO_8859_1);
        }

        @Override
        public InputStream getResourceAsStream(final String name) {
            if (!resourceName.equals(name)) {
                return null;
            }
            return new ByteArrayInputStream(resourceBytes);
        }
    }
}
