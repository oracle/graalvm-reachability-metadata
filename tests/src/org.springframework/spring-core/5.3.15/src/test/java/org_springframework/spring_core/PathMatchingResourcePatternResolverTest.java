/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

public class PathMatchingResourcePatternResolverTest {

    private static final String RESOURCE_PATH =
            "org_springframework/spring_core/class-path-resource-test.txt";

    private static final String RESOURCE_LOCATION =
            ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + RESOURCE_PATH;

    @Test
    void resolvesAllClasspathResourcesThroughConfiguredClassLoader() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(
                PathMatchingResourcePatternResolverTest.class.getClassLoader());

        Resource[] resources = resolver.getResources(RESOURCE_LOCATION);

        assertThat(resources).isNotEmpty();
        assertThat(resources[0].exists()).isTrue();
        assertThat(readContent(resources[0])).isEqualTo("spring-core class path resource test\n");
    }

    @Test
    void resolvesAllClasspathResourcesThroughSystemClassLoaderFallback() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(
                new SystemClassLoaderFallbackResourceLoader());

        Resource[] resources = resolver.getResources(
                ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + RESOURCE_PATH + ".missing");

        assertThat(resources).isEmpty();
    }

    private static String readContent(Resource resource) throws Exception {
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static final class SystemClassLoaderFallbackResourceLoader extends DefaultResourceLoader {

        @Override
        public ClassLoader getClassLoader() {
            return null;
        }
    }
}
