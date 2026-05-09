/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_autoconfigure;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationImportSelector;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;

import static org.assertj.core.api.Assertions.assertThat;

@EnableAutoConfiguration
public class AutoConfigurationMetadataLoaderTest {

    private static final String TEST_AUTO_CONFIGURATION = "com.example.TestAutoConfiguration";

    private final AnnotationMetadata annotationMetadata = AnnotationMetadata.introspect(getClass());

    @Test
    void loadsMetadataUsingProvidedBeanClassLoader() {
        MetadataCapturingImportFilter filter = new MetadataCapturingImportFilter();
        TestAutoConfigurationImportSelector selector = new TestAutoConfigurationImportSelector(filter);
        selector.setBeanClassLoader(getClass().getClassLoader());

        String[] imports = selector.selectImports(this.annotationMetadata);

        assertThat(imports).containsExactly(TEST_AUTO_CONFIGURATION);
        assertThat(filter.getAutoConfigurationClasses()).containsExactly(TEST_AUTO_CONFIGURATION);
        assertThat(filter.getAutoConfigurationMetadata()).isNotNull();
    }

    @Test
    void loadsMetadataUsingSystemResourcesWhenBeanClassLoaderIsNull() {
        MetadataCapturingImportFilter filter = new MetadataCapturingImportFilter();
        TestAutoConfigurationImportSelector selector = new TestAutoConfigurationImportSelector(filter);
        selector.setBeanClassLoader(null);

        String[] imports = selector.selectImports(this.annotationMetadata);

        assertThat(imports).containsExactly(TEST_AUTO_CONFIGURATION);
        assertThat(filter.getAutoConfigurationClasses()).containsExactly(TEST_AUTO_CONFIGURATION);
        assertThat(filter.getAutoConfigurationMetadata()).isNotNull();
    }

    private static final class TestAutoConfigurationImportSelector extends AutoConfigurationImportSelector {

        private final AutoConfigurationImportFilter filter;

        private TestAutoConfigurationImportSelector(AutoConfigurationImportFilter filter) {
            this.filter = filter;
        }

        @Override
        protected List<String> getCandidateConfigurations(AnnotationMetadata metadata,
                AnnotationAttributes attributes) {
            return List.of(TEST_AUTO_CONFIGURATION);
        }

        @Override
        protected List<AutoConfigurationImportFilter> getAutoConfigurationImportFilters() {
            return List.of(this.filter);
        }

    }

    private static final class MetadataCapturingImportFilter implements AutoConfigurationImportFilter {

        private String[] autoConfigurationClasses;

        private AutoConfigurationMetadata autoConfigurationMetadata;

        @Override
        public boolean[] match(String[] autoConfigurationClasses,
                AutoConfigurationMetadata autoConfigurationMetadata) {
            this.autoConfigurationClasses = autoConfigurationClasses;
            this.autoConfigurationMetadata = autoConfigurationMetadata;
            boolean[] matches = new boolean[autoConfigurationClasses.length];
            Arrays.fill(matches, true);
            return matches;
        }

        private String[] getAutoConfigurationClasses() {
            return this.autoConfigurationClasses;
        }

        private AutoConfigurationMetadata getAutoConfigurationMetadata() {
            return this.autoConfigurationMetadata;
        }

    }

}
