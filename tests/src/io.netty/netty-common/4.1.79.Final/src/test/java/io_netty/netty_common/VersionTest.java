/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_common;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import io.netty.util.Version;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class VersionTest {
    private static final String VERSION_RESOURCE_NAME = "META-INF/io.netty.versions.properties";

    @Test
    void identifyUsesTheProvidedClassLoaderToLoadNettyVersionMetadata() {
        RecordingClassLoader classLoader = new RecordingClassLoader();

        Map<String, Version> versions = Version.identify(classLoader);

        Assertions.assertEquals(List.of(VERSION_RESOURCE_NAME), classLoader.requestedResources());
        Assertions.assertFalse(versions.isEmpty(), "Expected Netty version metadata to be discovered");

        Version nettyCommonVersion = versions.get("netty-common");
        Assertions.assertNotNull(nettyCommonVersion, "Expected netty-common metadata to be present");
        Assertions.assertEquals("netty-common", nettyCommonVersion.artifactId());
        Assertions.assertFalse(nettyCommonVersion.artifactVersion().isBlank(), "Expected an artifact version");
        Assertions.assertTrue(nettyCommonVersion.buildTimeMillis() > 0, "Expected a parsed build time");
        Assertions.assertTrue(nettyCommonVersion.commitTimeMillis() > 0, "Expected a parsed commit time");
        Assertions.assertFalse(nettyCommonVersion.shortCommitHash().isBlank(), "Expected a short commit hash");
        Assertions.assertFalse(nettyCommonVersion.longCommitHash().isBlank(), "Expected a long commit hash");
        Assertions.assertFalse(nettyCommonVersion.repositoryStatus().isBlank(), "Expected a repository status");
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
