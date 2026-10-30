/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_common;

import com.alibaba.nacos.common.packagescan.resource.DefaultResourceLoader;
import com.alibaba.nacos.common.packagescan.resource.PathMatchingResourcePatternResolver;
import com.alibaba.nacos.common.packagescan.resource.Resource;
import com.alibaba.nacos.common.packagescan.resource.ResourceLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PathMatchingResourcePatternResolverTest {
    private static final String RESOURCE = "com_alibaba_nacos/nacos_common/settings.properties";

    @TempDir
    Path directory;

    @Test
    void findsAllResourcesWithAnExplicitLoader() throws Exception {
        verifyClasspathResource(new PathMatchingResourcePatternResolver(getClass().getClassLoader()));
    }

    @Test
    void findsAllResourcesUsingTheSystemLoader() throws Exception {
        ResourceLoader loader = new ResourceLoader() {
            @Override
            public Resource getResource(String location) {
                return new DefaultResourceLoader().getResource(location);
            }

            @Override
            public ClassLoader getClassLoader() {
                return null;
            }
        };
        verifyClasspathResource(new PathMatchingResourcePatternResolver(loader));
    }

    @Test
    void matchesFilesRecursively() throws Exception {
        Files.createDirectories(directory.resolve("nested"));
        Files.writeString(directory.resolve("root.properties"), "root=true\n");
        Files.writeString(directory.resolve("nested").resolve("child.properties"), "child=true\n");
        Files.writeString(directory.resolve("ignored.txt"), "ignored\n");
        Resource[] matches = new PathMatchingResourcePatternResolver()
                .getResources(directory.toUri() + "**/*.properties");
        assertEquals(2, matches.length);
        for (Resource match : matches) {
            assertTrue(match.getFilename().endsWith(".properties"));
        }
    }

    private void verifyClasspathResource(PathMatchingResourcePatternResolver resolver) throws Exception {
        Resource[] matches = resolver.getResources("classpath*:" + RESOURCE);
        assertEquals(1, matches.length);
        try (InputStream stream = matches[0].getInputStream()) {
            assertEquals("mode=cluster\n", new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
