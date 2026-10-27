/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_test;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.FilteredClassLoader;

public class FilteredClassLoaderTest {
    private static final String RESOURCE = "META-INF/spring.factories";

    @Test
    void delegatesResourceLookupsWhenTheResourceIsVisible() throws Exception {
        try (FilteredClassLoader classLoader = new FilteredClassLoader((name) -> false)) {
            URL resource = classLoader.getResource(RESOURCE);

            assertThat(resource).isNotNull();
            assertThat(Collections.list(classLoader.getResources(RESOURCE))).contains(resource);
        }
    }

    @Test
    void hidesResourcesSelectedByTheFilter() throws Exception {
        try (FilteredClassLoader classLoader = new FilteredClassLoader(RESOURCE::equals)) {
            assertThat(classLoader.getResource(RESOURCE)).isNull();
            assertThat(Collections.list(classLoader.getResources(RESOURCE))).isEmpty();
        }
    }
}
