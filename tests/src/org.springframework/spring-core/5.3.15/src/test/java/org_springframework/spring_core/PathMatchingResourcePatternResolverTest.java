/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/** Verifies classpath-wide resource resolution. */
public class PathMatchingResourcePatternResolverTest {
    @Test
    void findsAllMatchingClasspathResources() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        Resource[] resources = resolver.getResources("classpath*:spring-core-coverage.properties");

        assertThat(resources).hasSize(1);
        try (InputStream input = resources[0].getInputStream()) {
            assertThat(input.readAllBytes()).isNotEmpty();
        }
    }
}
