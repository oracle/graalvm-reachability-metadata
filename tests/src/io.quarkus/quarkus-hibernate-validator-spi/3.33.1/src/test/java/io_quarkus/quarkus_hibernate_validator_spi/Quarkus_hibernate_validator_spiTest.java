/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_hibernate_validator_spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BiPredicate;

import org.jboss.jandex.DotName;
import org.junit.jupiter.api.Test;

import io.quarkus.builder.BuildChain;
import io.quarkus.builder.BuildResult;
import io.quarkus.builder.item.MultiBuildItem;
import io.quarkus.builder.item.SimpleBuildItem;
import io.quarkus.hibernate.validator.spi.AdditionalConstrainedClassBuildItem;
import io.quarkus.hibernate.validator.spi.BeanValidationAnnotationsBuildItem;
import io.quarkus.hibernate.validator.spi.BeanValidationTraversableResolverBuildItem;

public class Quarkus_hibernate_validator_spiTest {
    @Test
    void additionalConstrainedClassFromClassExposesClassMetadata() {
        AdditionalConstrainedClassBuildItem item = AdditionalConstrainedClassBuildItem.of(SampleConstrainedBean.class);

        assertThat(item.isGenerated()).isFalse();
        assertThat(item.getClazz()).isSameAs(SampleConstrainedBean.class);
        assertThat(item.getName()).isEqualTo(SampleConstrainedBean.class.getName());
        assertThat(item.getBytes()).isEmpty();
    }

    @Test
    void additionalConstrainedClassFromGeneratedBytesExposesGeneratedMetadata() {
        byte[] generatedClassBytes = new byte[] { 0x01, 0x23, 0x45 };

        AdditionalConstrainedClassBuildItem item = AdditionalConstrainedClassBuildItem.of(
                "com.example.GeneratedConstrainedBean", generatedClassBytes);

        assertThat(item.isGenerated()).isTrue();
        assertThat(item.getClazz()).isNull();
        assertThat(item.getName()).isEqualTo("com.example.GeneratedConstrainedBean");
        assertThat(item.getBytes()).isSameAs(generatedClassBytes).containsExactly(0x01, 0x23, 0x45);
    }

    @Test
    void additionalConstrainedClassesCanBeCollectedAsMultipleBuildItems() throws Exception {
        AdditionalConstrainedClassBuildItem beanItem = AdditionalConstrainedClassBuildItem.of(SampleConstrainedBean.class);
        AdditionalConstrainedClassBuildItem generatedItem = AdditionalConstrainedClassBuildItem.of(
                "com.example.GeneratedConstrainedBean", new byte[] { 0x11, 0x22 });
        BuildChain chain = BuildChain.builder()
                .addBuildStep(context -> {
                    context.produce(beanItem);
                    context.produce(generatedItem);
                })
                .produces(AdditionalConstrainedClassBuildItem.class)
                .build()
                .addFinal(AdditionalConstrainedClassBuildItem.class)
                .build();
        BuildResult result = chain.createExecutionBuilder("validation-build-items").execute();

        assertThat(result.consumeMulti(AdditionalConstrainedClassBuildItem.class))
                .containsExactlyInAnyOrder(beanItem, generatedItem);
    }

    @Test
    void beanValidationAnnotationsExposeValidConstraintAndAllAnnotationSets() {
        DotName valid = DotName.createSimple("jakarta.validation.Valid");
        DotName notNull = DotName.createSimple("jakarta.validation.constraints.NotNull");
        DotName size = DotName.createSimple("jakarta.validation.constraints.Size");
        DotName customConstraint = DotName.createSimple("com.example.validation.CustomConstraint");
        Set<DotName> constraints = new LinkedHashSet<>(Set.of(notNull, size, customConstraint));
        Set<DotName> allAnnotations = new LinkedHashSet<>(Set.of(valid, notNull, size, customConstraint));

        BeanValidationAnnotationsBuildItem item = new BeanValidationAnnotationsBuildItem(valid, constraints, allAnnotations);

        assertThat(item.getValidAnnotation()).isEqualTo(valid);
        assertThat(item.getConstraintAnnotations()).containsExactlyInAnyOrder(notNull, size, customConstraint);
        assertThat(item.getAllAnnotations()).containsExactlyInAnyOrder(valid, notNull, size, customConstraint);
    }

    @Test
    void beanValidationAnnotationSetsAreReadOnly() {
        DotName valid = DotName.createSimple("jakarta.validation.Valid");
        DotName notBlank = DotName.createSimple("jakarta.validation.constraints.NotBlank");
        DotName email = DotName.createSimple("jakarta.validation.constraints.Email");
        BeanValidationAnnotationsBuildItem item = new BeanValidationAnnotationsBuildItem(
                valid, Set.of(notBlank), Set.of(valid, notBlank));

        assertThatThrownBy(() -> item.getConstraintAnnotations().add(email))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> item.getAllAnnotations().remove(valid))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void buildItemsExposeQuarkusBuildItemMultiplicity() {
        AdditionalConstrainedClassBuildItem additionalClass = AdditionalConstrainedClassBuildItem.of(
                SampleConstrainedBean.class);
        BeanValidationAnnotationsBuildItem annotations = new BeanValidationAnnotationsBuildItem(
                DotName.createSimple("jakarta.validation.Valid"), Set.of(), Set.of());
        BeanValidationTraversableResolverBuildItem traversableResolver = new BeanValidationTraversableResolverBuildItem(
                (bean, attributeName) -> true);

        assertThat(additionalClass).isInstanceOf(MultiBuildItem.class);
        assertThat(annotations).isInstanceOf(SimpleBuildItem.class).isNotInstanceOf(MultiBuildItem.class);
        assertThat(traversableResolver).isInstanceOf(SimpleBuildItem.class).isNotInstanceOf(MultiBuildItem.class);
    }

    @Test
    void traversableResolverBuildItemRetainsAndUsesAttributeLoadedPredicate() {
        SampleConstrainedBean loadedBean = new SampleConstrainedBean(true);
        SampleConstrainedBean unloadedBean = new SampleConstrainedBean(false);
        BiPredicate<Object, String> predicate = (bean, attributeName) -> bean instanceof SampleConstrainedBean sample
                && sample.loaded()
                && "validatedProperty".equals(attributeName);

        BeanValidationTraversableResolverBuildItem item = new BeanValidationTraversableResolverBuildItem(predicate);

        assertThat(item.getAttributeLoadedPredicate()).isSameAs(predicate);
        assertThat(item.getAttributeLoadedPredicate().test(loadedBean, "validatedProperty")).isTrue();
        assertThat(item.getAttributeLoadedPredicate().test(loadedBean, "otherProperty")).isFalse();
        assertThat(item.getAttributeLoadedPredicate().test(unloadedBean, "validatedProperty")).isFalse();
        assertThat(item.getAttributeLoadedPredicate().test("not a bean", "validatedProperty")).isFalse();
    }

    private record SampleConstrainedBean(boolean loaded) {
    }
}
