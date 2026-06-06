/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_helidon_config.helidon_config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;

import io.helidon.config.ClasspathConfigSource;

import org.junit.jupiter.api.Test;

public class ClasspathConfigSourceTest {
    private static final String BASE_RESOURCE = "io_helidon_config/helidon_config/classpath-source.yaml";
    private static final String MISSING_RELATIVE_RESOURCE = "missing-relative.yaml";
    private static final String MISSING_RESOURCE = "io_helidon_config/helidon_config/missing-classpath-source.yaml";

    @Test
    void relativeResolverLooksForResourceRelativeToClasspathSource() {
        final ClasspathConfigSource source = ClasspathConfigSource.create(BASE_RESOURCE);
        final Function<String, Optional<InputStream>> resolver = source.relativeResolver();

        final Optional<InputStream> resolved = resolver.apply(MISSING_RELATIVE_RESOURCE);

        assertThat(resolved).isEmpty();
    }

    @Test
    void createAllLooksForAllMatchingClasspathResources() {
        final Collection<? super ClasspathConfigSource> sources = ClasspathConfigSource.createAll(MISSING_RESOURCE);

        assertThat(sources).hasSize(1);
    }
}
