/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_data_couchbase_test;

import java.lang.annotation.Annotation;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.couchbase.autoconfigure.DataCouchbaseProperties;
import org.springframework.boot.data.couchbase.test.autoconfigure.DataCouchbaseTest;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.test.autoconfigure.OverrideAutoConfiguration;
import org.springframework.boot.test.autoconfigure.TestSliceTestContextBootstrapper;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.data.couchbase.core.convert.MappingCouchbaseConverter;
import org.springframework.test.context.BootstrapWith;
import org.springframework.test.context.TestContextAnnotationUtils;

import static org.assertj.core.api.Assertions.assertThat;

/// Covers what the Data Couchbase slice's context bootstrapper prepares for a test.
///
/// A native image without Spring AOT test output cannot construct a Spring Boot test context
/// bootstrapper, so the slice's bootstrapper binding and properties are resolved the way the
/// bootstrapper resolves them and applied to a plain application context.
public class DataCouchbaseTestContextBootstrapperTest {
    private static final String SLICE_BOOTSTRAPPER =
            "org.springframework.boot.data.couchbase.test.autoconfigure.DataCouchbaseTestContextBootstrapper";

    @Test
    void sliceBootstrapsWithATestSliceBootstrapperBoundToItsAnnotation() {
        Class<?> bootstrapper = bootstrapperClass(CatalogSlice.class);

        assertThat(bootstrapper.getName()).isEqualTo(SLICE_BOOTSTRAPPER);
        assertThat(TestSliceTestContextBootstrapper.class).isAssignableFrom(bootstrapper);
        assertThat(sliceAnnotationType(bootstrapper)).isEqualTo(DataCouchbaseTest.class);
    }

    @Test
    void preparesSlicePropertiesForTheDataCouchbaseInfrastructure() {
        String[] properties = sliceProperties(CatalogSlice.class);

        assertThat(properties).containsExactly(
                "spring.data.couchbase.bucket-name=products", "spring.data.couchbase.type-key=_type");
        new ApplicationContextRunner()
                .withPropertyValues(properties)
                .withUserConfiguration(CatalogSlice.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(DataCouchbaseProperties.class).getBucketName()).isEqualTo("products");
                    assertThat(context.getBean(MappingCouchbaseConverter.class).getTypeKey()).isEqualTo("_type");
                });
    }

    @Test
    void innerAndInheritingTestClassesUseTheSliceProperties() {
        assertThat(sliceProperties(CatalogSlice.InnerTest.class)).containsExactly(
                "spring.data.couchbase.bucket-name=products", "spring.data.couchbase.type-key=_type");
        assertThat(sliceProperties(InheritingSlice.class)).containsExactly(
                "spring.data.couchbase.bucket-name=products", "spring.data.couchbase.type-key=_type");
        assertThat(sliceProperties(UnannotatedTest.class)).isNull();
    }

    @Test
    void disablesUnrelatedAutoConfigurationAndKeepsTheSliceImports() {
        OverrideAutoConfiguration override =
                TestContextAnnotationUtils.findMergedAnnotation(CatalogSlice.class, OverrideAutoConfiguration.class);
        assertThat(override).isNotNull();
        assertThat(override.enabled()).isFalse();
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withUserConfiguration(FullAutoConfiguration.class, CatalogSlice.class);

        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(PropertySourcesPlaceholderConfigurer.class);
        });
        contextRunner
                .withPropertyValues(EnableAutoConfiguration.ENABLED_OVERRIDE_PROPERTY + "=" + override.enabled())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(PropertySourcesPlaceholderConfigurer.class);
                    assertThat(context).hasSingleBean(SslBundles.class);
                    assertThat(context).hasSingleBean(DataCouchbaseProperties.class);
                });
    }

    /// The bootstrapper class that `@BootstrapWith` names for the test class.
    private static Class<?> bootstrapperClass(Class<?> testClass) {
        BootstrapWith bootstrapWith = TestContextAnnotationUtils.findMergedAnnotation(testClass, BootstrapWith.class);
        assertThat(bootstrapWith).isNotNull();
        return bootstrapWith.value();
    }

    /// The slice annotation a test slice bootstrapper binds through its type argument.
    @SuppressWarnings("unchecked")
    private static Class<? extends Annotation> sliceAnnotationType(Class<?> bootstrapper) {
        Class<?> annotationType = ResolvableType.forClass(bootstrapper)
                .as(TestSliceTestContextBootstrapper.class)
                .getGeneric(0)
                .resolve();
        assertThat(annotationType).isNotNull();
        return (Class<? extends Annotation>) annotationType;
    }

    /// The properties the slice bootstrapper adds for a test class: the `properties` of its bound
    /// annotation, searched through the type hierarchy and enclosing classes.
    private static String[] sliceProperties(Class<?> testClass) {
        Class<? extends Annotation> annotationType = sliceAnnotationType(bootstrapperClass(DataCouchbaseTest.class));
        MergedAnnotation<? extends Annotation> annotation = MergedAnnotations.search(SearchStrategy.TYPE_HIERARCHY)
                .withEnclosingClasses(TestContextAnnotationUtils::searchEnclosingClass)
                .from(testClass)
                .get(annotationType);
        return annotation.isPresent() ? annotation.getStringArray("properties") : null;
    }

    @DataCouchbaseTest(properties = {"spring.data.couchbase.bucket-name=products", "spring.data.couchbase.type-key=_type"})
    public static class CatalogSlice {
        public class InnerTest {
        }
    }

    public static class InheritingSlice extends CatalogSlice {
    }

    public static class UnannotatedTest {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    public static class FullAutoConfiguration {
    }
}
