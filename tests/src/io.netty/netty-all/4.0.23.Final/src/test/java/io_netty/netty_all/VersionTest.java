/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import io.netty.util.Version;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

public class VersionTest {
    private static final String VERSION_RESOURCE = "META-INF/io.netty.versions.properties";
    private static final String ARTIFACT_ID = "netty-version-probe";
    private static final String ARTIFACT_VERSION = "metadata-probe";

    @Test
    void identifyLoadsVersionMetadataFromConfiguredClassLoader() {
        VersionResourceClassLoader classLoader = new VersionResourceClassLoader(versionPropertiesUrl());

        Map<String, Version> versions = Version.identify(classLoader);

        Assertions.assertEquals(Collections.singleton(ARTIFACT_ID), versions.keySet());
        Assertions.assertEquals(Collections.singletonList(VERSION_RESOURCE), classLoader.requestedResourceNames());

        Version version = versions.get(ARTIFACT_ID);
        Assertions.assertEquals(ARTIFACT_ID, version.artifactId());
        Assertions.assertEquals(ARTIFACT_VERSION, version.artifactVersion());
        Assertions.assertEquals("abc1234", version.shortCommitHash());
        Assertions.assertEquals("abc1234567890", version.longCommitHash());
        Assertions.assertEquals("clean", version.repositoryStatus());
        Assertions.assertTrue(version.buildTimeMillis() > 0);
        Assertions.assertTrue(version.commitTimeMillis() > version.buildTimeMillis());
        Assertions.assertEquals(ARTIFACT_ID + '-' + ARTIFACT_VERSION + ".abc1234", version.toString());
    }

    @Test
    void identifyIgnoresIncompleteVersionMetadata() {
        VersionResourceClassLoader classLoader = new VersionResourceClassLoader(resourceUrl(
                ARTIFACT_ID + ".version=" + ARTIFACT_VERSION + '\n' +
                ARTIFACT_ID + ".buildDate=2023-01-02 03:04:05 +0000\n"));

        Map<String, Version> versions = Version.identify(classLoader);

        Assertions.assertTrue(versions.isEmpty());
        Assertions.assertEquals(Collections.singletonList(VERSION_RESOURCE), classLoader.requestedResourceNames());
    }

    private static URL versionPropertiesUrl() {
        return resourceUrl(
                ARTIFACT_ID + ".version=" + ARTIFACT_VERSION + '\n' +
                ARTIFACT_ID + ".buildDate=2023-01-02 03:04:05 +0000\n" +
                ARTIFACT_ID + ".commitDate=2023-01-02 04:05:06 +0000\n" +
                ARTIFACT_ID + ".shortCommitHash=abc1234\n" +
                ARTIFACT_ID + ".longCommitHash=abc1234567890\n" +
                ARTIFACT_ID + ".repoStatus=clean\n");
    }

    private static URL resourceUrl(String contents) {
        try {
            return new URL(null, "memory:netty-version", new InMemoryUrlStreamHandler(contents));
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static final class VersionResourceClassLoader extends ClassLoader {
        private final URL versionPropertiesUrl;
        private final List<String> requestedResourceNames = new ArrayList<String>();

        private VersionResourceClassLoader(URL versionPropertiesUrl) {
            super(VersionTest.class.getClassLoader());
            this.versionPropertiesUrl = versionPropertiesUrl;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            requestedResourceNames.add(name);
            if (VERSION_RESOURCE.equals(name)) {
                return Collections.enumeration(Collections.singleton(versionPropertiesUrl));
            }
            return Collections.emptyEnumeration();
        }

        private List<String> requestedResourceNames() {
            return requestedResourceNames;
        }
    }

    private static final class InMemoryUrlStreamHandler extends URLStreamHandler {
        private final byte[] contents;

        private InMemoryUrlStreamHandler(String contents) {
            this.contents = contents.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        protected URLConnection openConnection(URL url) {
            return new URLConnection(url) {
                @Override
                public void connect() {
                }

                @Override
                public InputStream getInputStream() {
                    return new ByteArrayInputStream(contents);
                }
            };
        }
    }
}
