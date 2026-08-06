/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package netty;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import io.netty.util.Version;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class VersionTest {
    private static final String VERSION_RESOURCE_NAME = "META-INF/io.netty.versions.properties";

    @Test
    public void identifiesNettyArtifactsFromVersionMetadataResources() {
        RecordingClassLoader classLoader = new RecordingClassLoader();

        Map<String, Version> versions = Version.identify(classLoader);

        assertThat(classLoader.requestedResources()).containsExactly(VERSION_RESOURCE_NAME);
        assertThat(versions).containsKey("netty-common");

        Version nettyCommonVersion = versions.get("netty-common");
        assertThat(nettyCommonVersion.artifactId()).isEqualTo("netty-common");
        assertThat(nettyCommonVersion.artifactVersion()).isNotBlank();
        assertThat(nettyCommonVersion.buildTimeMillis()).isPositive();
        assertThat(nettyCommonVersion.commitTimeMillis()).isPositive();
        assertThat(nettyCommonVersion.shortCommitHash()).isNotBlank();
        assertThat(nettyCommonVersion.longCommitHash()).isNotBlank();
        assertThat(nettyCommonVersion.repositoryStatus()).isNotBlank();
        assertThat(nettyCommonVersion.toString())
                .contains(nettyCommonVersion.artifactId())
                .contains(nettyCommonVersion.artifactVersion())
                .contains(nettyCommonVersion.shortCommitHash());
    }

    private static final class RecordingClassLoader extends ClassLoader {
        private final List<String> requestedResources = new ArrayList<>();

        private RecordingClassLoader() {
            super(VersionTest.class.getClassLoader());
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            requestedResources.add(name);
            return super.getResources(name);
        }

        private List<String> requestedResources() {
            return requestedResources;
        }
    }
}
