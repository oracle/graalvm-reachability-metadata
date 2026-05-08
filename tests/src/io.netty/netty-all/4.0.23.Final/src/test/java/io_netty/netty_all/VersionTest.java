/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import io.netty.util.Version;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class VersionTest {
    private static final String VERSION_RESOURCE_NAME = "META-INF/io.netty.versions.properties";

    @Test
    void identifyReadsVersionPropertiesFromConfiguredClassLoader() {
        String properties = """
                sample-artifact.version=1.0-test
                sample-artifact.buildDate=2014-10-01 12:00:00 +0000
                sample-artifact.commitDate=2014-10-01 11:00:00 +0000
                sample-artifact.shortCommitHash=abc1234
                sample-artifact.longCommitHash=abc123456789
                sample-artifact.repoStatus=clean
                incomplete.version=ignored
                """;
        VersionResourceClassLoader classLoader = new VersionResourceClassLoader(properties);

        Map<String, Version> versions = Version.identify(classLoader);

        assertThat(classLoader.requestedResource()).isEqualTo(VERSION_RESOURCE_NAME);
        assertThat(versions).containsOnlyKeys("sample-artifact");

        Version version = versions.get("sample-artifact");
        assertThat(version.artifactId()).isEqualTo("sample-artifact");
        assertThat(version.artifactVersion()).isEqualTo("1.0-test");
        assertThat(version.buildTimeMillis()).isPositive();
        assertThat(version.commitTimeMillis()).isPositive();
        assertThat(version.shortCommitHash()).isEqualTo("abc1234");
        assertThat(version.longCommitHash()).isEqualTo("abc123456789");
        assertThat(version.repositoryStatus()).isEqualTo("clean");
        assertThat(version.toString()).isEqualTo("sample-artifact-1.0-test.abc1234");
    }

    private static final class VersionResourceClassLoader extends ClassLoader {
        private final String properties;
        private String requestedResource;

        private VersionResourceClassLoader(String properties) {
            super(VersionTest.class.getClassLoader());
            this.properties = properties;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            requestedResource = name;
            if (!VERSION_RESOURCE_NAME.equals(name)) {
                return Collections.emptyEnumeration();
            }
            return Collections.enumeration(Collections.singleton(resourceUrl()));
        }

        private String requestedResource() {
            return requestedResource;
        }

        private URL resourceUrl() throws IOException {
            return new URL(null, "memory:netty-version-properties", new URLStreamHandler() {
                @Override
                protected URLConnection openConnection(URL url) {
                    return new URLConnection(url) {
                        @Override
                        public void connect() {
                        }

                        @Override
                        public InputStream getInputStream() {
                            return new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8));
                        }
                    };
                }
            });
        }
    }
}
