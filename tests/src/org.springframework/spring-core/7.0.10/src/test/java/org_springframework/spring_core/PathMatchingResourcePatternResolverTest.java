/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/** Verifies classpath resource discovery with explicit and system class loaders. */
public class PathMatchingResourcePatternResolverTest {
    private static final String RESOURCE = "org_springframework/spring_core/dynamic-access.properties";

    @Test
    void discoversClasspathResourcesWithExplicitClassLoader() throws Exception {
        PathMatchingResourcePatternResolver resolver =
                new PathMatchingResourcePatternResolver(getClass().getClassLoader());

        Resource[] resources = resolver.getResources("classpath*:" + RESOURCE);

        assertThat(resources).isNotEmpty();
        assertThat(resources[0].getFilename()).isEqualTo("dynamic-access.properties");
    }

    @Test
    void discoversClasspathResourcesWithSystemClassLoaderFallback() throws Exception {
        PathMatchingResourcePatternResolver resolver =
                new PathMatchingResourcePatternResolver(new NullClassLoaderResourceLoader());

        Resource[] resources = resolver.getResources("classpath*:" + RESOURCE);

        assertThat(resources).isNotEmpty();
    }

    private static final class NullClassLoaderResourceLoader implements ResourceLoader {
        @Override
        public Resource getResource(String location) {
            return new ClassPathResource(location, PathMatchingResourcePatternResolverTest.class.getClassLoader());
        }

        @Override
        public ClassLoader getClassLoader() {
            return null;
        }
    }
}
